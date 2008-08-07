package suneido.database.query;

import static suneido.SuException.unreachable;
import static suneido.Suneido.verify;
import static suneido.Util.*;
import static suneido.database.Record.MAX_FIELD;
import static suneido.database.Record.MIN_FIELD;

import java.nio.ByteBuffer;
import java.util.*;

import suneido.*;
import suneido.database.Record;
import suneido.database.Transaction;
import suneido.database.query.expr.*;

public class Select extends Query1 {
	private Multi expr;
	private boolean optFirst = true;
	private boolean conflicting = false;
	boolean fixdone;
	List<Fixed> fix;
	private List<String> source_index = null;	// may have extra stuff on
												// the end, or be
												// missing fields that are fixed
	private List<List<String>> filter = null;
	private List<String> select_needs;
	private Table tbl;
	private List<String> primary;
	private List<List<String>> theindexes;
	private Map<String, Iselect> isels;
	private List<List<String>> possible;
	private Map<String, Double> ffracs;
	private Map<List<String>, Double> ifracs;
	private List<String> prior_needs;
	private int nrecs = 0;
	// for get
	private boolean first = true;
	private boolean rewound = true;
	private List<Keyrange> ranges;
	private int range_i = 0;
	private final Keyrange sel = new Keyrange();
	private boolean newrange = true;;
	int n_in = 0;
	int n_out = 0;
	private HashSet<Long> f;
	private Header hdr;
	private Transaction tran;

	public Select(Query source, Expr expr) {
		super(source);
		expr = expr.fold();
		if (!(expr instanceof And))
			expr = new And().add(expr);
		 if (!source.columns().containsAll(expr.fields()))
			throw new SuException("select: nonexistent columns: "
					+ listToParens(difference(expr.fields(), source.columns())));
		this.expr = (Multi) expr;
	}

	@Override
	public String toString() {
		String s = source + " WHERE";
		if (conflicting)
			return s + " nothing";
		if (! nil(source_index))
			s += "^" + listToParens(source_index);
		if (! nil(filter))
			s += "%" + listToParens(filter);
		if (! nil(expr.exprs))
			s += " " + expr;
		return s;
	}

	@Override
	List<Fixed> fixed() {
		if (fix != null)
			return fix;
		fix = new ArrayList<Fixed>();
		List<String> fields = source.columns();
		for (Expr e : expr.exprs)
			if (e.isTerm(fields) && e instanceof BinOp) {
				// TODO: handle IN
				BinOp binop = (BinOp) e;
				if (binop.op == BinOp.Op.IS) {
					String field = ((Identifier) binop.left).ident;
					SuValue value = ((Constant) binop.right).value;
					fix.add(new Fixed(field, value));
				}
			}
		fix = Fixed.combine(fix, source.fixed());
		return fix;
	}

	@Override
	Query transform() {
		boolean moved = false;
		// remove empty selects
		if (nil(expr.exprs))
			return source.transform();
		// combine selects
		if (source instanceof Select) {
			Select s = (Select) source;
			expr = new And(concat(s.expr.exprs, expr.exprs));
			source = s.source;
			return transform();
		}
		// move selects before projects
		else if (source instanceof Project) {
			Project p = (Project) source;
			source = p.source;
			p.source = this;
			return p.transform();
		}
		// move selects before renames
		else if (source instanceof Rename) {
			Rename r = (Rename) source;
			expr.rename(r.to, r.from);
			source = r.source;
			r.source = this;
			return r.transform();
		}
		// move select before extend, unless it depends on rules
		else if (source instanceof Extend) {
			Extend extend = (Extend) source;
			List<Expr> src1 = new ArrayList<Expr>();
			List<Expr> rest = new ArrayList<Expr>();
			for (Expr e : expr.exprs)
				if (nil(intersect(extend.rules, e.fields())))
					src1.add(e.replace(extend.flds, extend.exprs));
				else
					rest.add(e);
			if (!nil(src1))
				extend.source = new Select(extend.source, new And(src1));
			if (!nil(rest))
				expr = new And(rest);
			else
				moved = true;
		}
		// split select before & after summarize
		else if (source instanceof Summarize) {
			Summarize summarize = (Summarize) source;
			List<String> flds1 = summarize.source.columns();
			List<Expr> src1 = new ArrayList<Expr>();
			List<Expr> rest = new ArrayList<Expr>();
			for (Expr e : expr.exprs)
				if (flds1.containsAll(e.fields()))
					src1.add(e);
				else
					rest.add(e);
			if (!nil(src1))
				summarize.source = new Select(summarize.source, new And(src1));
			if (!nil(rest))
				expr = new And(rest);
			else
				moved = true;
		}
		// distribute select over intersect
		else if (source instanceof Intersect) {
			Intersect q = (Intersect) source;
			q.source = new Select(q.source, expr);
			q.source2 = new Select(q.source2, expr);
			moved = true;
		}
		// distribute select over difference
		else if (source instanceof Difference) {
			Difference q = (Difference) source;
			q.source = new Select(q.source, expr);
			q.source2 = new Select(q.source2, project(q.source2));
			moved = true;
		}
		// distribute select over union
		else if (source instanceof Union) {
			Query2 q = (Query2) source;
			q.source = new Select(q.source, project(q.source));
			q.source2 = new Select(q.source2, project(q.source2));
			moved = true;
		}
		// split select over product
		else if (source instanceof Product) {
			Product x = (Product) source;
			moved = distribute(x);
		}
		// split select over leftjoin (left side only)
		else if (source instanceof LeftJoin) {
			LeftJoin j = (LeftJoin) source;
			List<String> flds1 = j.source.columns();
			List<Expr> common = new ArrayList<Expr>();
			List<Expr> src1 = new ArrayList<Expr>();
			for (Expr e : expr.exprs)
				if (flds1.containsAll(e.fields()))
					src1.add(e);
				else
					common.add(e);
			if (!nil(src1))
				j.source = new Select(j.source, new And(src1));
			if (!nil(common))
				expr = new And(common);
			else
				moved = true;
		}
		// NOTE: must check for LeftJoin before Join since it's derived
		// split select over join
		else if (source instanceof Join) {
			Join j = (Join) source;
			moved = distribute(j);
		}
		source = source.transform();
		return moved ? source : this;
	}

	private Expr project(Query q) {
		List<String> srcflds = q.columns();
		List<String> exprflds = expr.fields();
		List<String> missing = difference(exprflds, srcflds);
		return expr.replace(missing,
				Collections.nCopies(missing.size(),
						(Expr) Constant.EMPTY));
	}

	private boolean distribute(Query2 q2) {
		List<String> flds1 = q2.source.columns();
		List<String> flds2 = q2.source2.columns();
		List<Expr> common = new ArrayList<Expr>();
		List<Expr> src1 = new ArrayList<Expr>();
		List<Expr> src2 = new ArrayList<Expr>();
		for (Expr e : expr.exprs) {
			boolean used = false;
			if (flds1.containsAll(e.fields())) {
				src1.add(e);
				used = true;
			}
			if (flds2.containsAll(e.fields())) {
				src2.add(e);
				used = true;
			}
			if (! used)
				common.add(e);
		}
		if (! nil(src1))
			q2.source = new Select(q2.source, new And(src1));
		if (! nil(src2))
			q2.source2 = new Select(q2.source2, new And(src2));
		if (! nil(common))
			expr = new And(common);
		else
			return true;
		return false;
	}

	// optimize =====================================================

	@Override
	double optimize2(List<String> index, List<String> needs,
			List<String> firstneeds, boolean is_cursor, boolean freeze) {
		if (optFirst) {
			prior_needs = noFields; // needs;
			select_needs = expr.fields();
			tbl = source instanceof Table ? (Table) source : null;
		}
		if (tbl == null || // source isnt a Table
				nil(tbl.indexes())) { // empty key() singleton, index irrelevant
			optFirst = false;
			source_index = index;
			return source.optimize(index, union(needs, select_needs), union(
					firstneeds, select_needs), is_cursor, freeze);
		}

		if (optFirst) {
			optFirst = false;
			optimize_setup();
		}

		if (conflicting)
			return 0;

		primary = null;
		filter = null;

		double cost = choose_primary(index);
		if (nil(primary))
			return IMPOSSIBLE;

		if (!is_cursor)
			cost = choose_filter(cost);

		if (!freeze)
			return cost;

		source_index = primary;
		tbl.select_index(source_index);

		return cost;
	}

	private void optimize_setup() {
		fixed(); // calc before altering expr

		theindexes = tbl.indexes();

		List<Cmp> cmps = extract_cmps(); // WARNING: modifies expr
		isels = new HashMap<String, Iselect>();
		cmps_to_isels(cmps);
		possible = new ArrayList<List<String>>();
		identify_possible();
		ffracs = new HashMap<String, Double>();
		calc_field_fracs();
		ifracs = new HashMap<List<String>, Double>();
		calc_index_fracs();

		// TODO: should be frac of complete select, not just indexes
		nrecs = (int) (datafrac(theindexes) * tbl.nrecords() + .5); // .5 to
		// round
	}
	private List<Cmp> extract_cmps() {
		List<Cmp> cmps = new ArrayList<Cmp>();
		List<String> fields = tbl.tbl.getFields();
		for (Iterator<Expr> iter = expr.exprs.iterator(); iter.hasNext();) {
			Expr expr = iter.next();
			if (expr.isTerm(fields))
				if (expr instanceof In) {
					In in = (In) expr;
					Identifier id = (Identifier) in.expr;
					cmps.add(new Cmp(id.ident, in.packed));
					iter.remove();
				} else if (expr instanceof BinOp) {
					BinOp binop = (BinOp) expr;
					if (binop.op != BinOp.Op.ISNT) {
						String field = ((Identifier) binop.left).ident;
						ByteBuffer value = ((Constant) binop.right).packed;
						cmps.add(new Cmp(field, binop.op, value));
						iter.remove();
					}
				}
		}
		return cmps;
	}
	private void cmps_to_isels(List<Cmp> cmps) {
		if (cmps.isEmpty())
			return ;
		Collections.sort(cmps);
		Iselect isel = new Iselect();
		Iterator<Cmp> iter = cmps.iterator();
		Cmp cmp = iter.next();
		boolean end = iter.hasNext();
		while (! end) {
			Iselect r = new Iselect();
			if (cmp.op == null) { // IN
				r.values = cmp.values;
				r.type = IselType.VALUES;
			} else
				switch (cmp.op) {
				case IS:	r.org.x = r.end.x = cmp.value; break;
				case LT:	r.end.x = cmp.value; r.end.d = -1; break;
				case LTE:	r.end.x = cmp.value; break;
				case GT:	r.org.x = cmp.value; r.org.d = +1; break;
				case GTE:	r.org.x = cmp.value; break;
				default:	throw unreachable();
				}
			isel.and_with(r);

			String ident = cmp.ident;
			if (!(end = !iter.hasNext()))
				cmp = iter.next();
			if (end || cmp.ident != ident) {
				// end of group
				if (isel.none())
					{ nrecs = 0; conflicting = true; return ; }
				if (isel.values != null)
					Collections.sort(isel.values);
				if (! isel.all())
					isels.put(cmp.ident, isel);
				isel = new Iselect();
			}
		}
	}
	private void identify_possible() {
		// possible = indexes with isels
		for (List<String> idx : theindexes)
			for (String fld : idx)
				if (isels.containsKey(fld)) {
					possible.add(idx);
					break;
				}
	}

	private void calc_field_fracs() {
		for (List<String> idx : possible)
			for (String fld : idx)
				if (isels.containsKey(fld) && !ffracs.containsKey(fld))
					ffracs.put(fld, field_frac(fld));
	}

	private double field_frac(String field) {
		List<String> best_index = null;
		int best_size = Integer.MAX_VALUE;
		// look for smallest index starting with field
		for (List<String> idx : theindexes)
			if (idx.get(0) == field && tbl.indexsize(idx) < best_size) {
				best_index = idx;
				best_size = tbl.indexsize(idx);
			}
		if (nil(best_index))
			return .5;
		Iselect fsel = isels.get(field);
		return iselsize(best_index, list(fsel));
	}

	private void calc_index_fracs() {
		// ifracs = fraction selected from each index
		for (List<String> idx : theindexes) {
			List<Iselect> multisel = iselects(idx);
			ifracs.put(idx, nil(multisel) ? (double) 1 : iselsize(idx, multisel));
		}
	}
	private List<Iselect> iselects(List<String> idx) {
		List<Iselect> multisel = new ArrayList<Iselect>();
		for (String field : idx) {
			Iselect r;
			if (null == (r = isels.get(field)))
				break;
			multisel.add(r);
			if (r.type == IselType.RANGE && !r.one())
				break; // can't add anything after range
		}
		return multisel;
	}

	public float iselsize(List<String> index, List<Iselect> iselects) {
		// first check for matching a known number of records
		if (keys().contains(index) && index.size() == iselects.size()) {
			int nexact = 1;
			for (Iselect isel : iselects) {
				verify(! isel.none());
				if (isel.type == IselType.VALUES)
					nexact *= isel.values.size();
				else if (isel.org != isel.end) { // range
					nexact = 0;
					break ;
				}
			}
			if (nexact > 0) {
				int nrecs = tbl.tbl.nrecords();
				return nrecs != 0 ? (float) nexact / nrecs : 0;
				// NOTE: assumes they all exist ???
			}
		}

		// TODO: convert this to use Select::selects()

		for (Iselect isel : iselects) {
			verify(! isel.none());
			if (isel.one())
			{ }
			else if (isel.type == IselType.RANGE)
				break ;
			else { // set - recurse through values
				List<ByteBuffer> save = isel.values;
				float sum = 0;
				for (ByteBuffer v : isel.values) {
					isel.values = list(v);
					sum += iselsize(index, iselects);
				}
				isel.values = save;
				return sum;
			}
		}

		// now build the key
		int i = 0;
		Record org = new Record();
		Record end = new Record();
		for (int iseli = 0; iseli < iselects.size(); ++iseli, ++i) {
			Iselect isel = iselects.get(iseli);
			verify(! isel.none());
			if (isel.one()) {
				if (isel.type == IselType.RANGE) {
					org.add(isel.org.x);
					end.add(isel.org.x);
				} else { // in set
					org.add(isel.values.get(0));
					end.add(isel.values.get(0));
				}
				if (iseli == iselects.size() - 1) {
					// final exact value (inclusive end)
					++i;
					for (int j = i; j < index.size(); ++j)
						end.addMax();
					if (i >= index.size()) // ensure at least one added
						end.addMax();
				}
			} else if (isel.type == IselType.RANGE) {
				// final range
				org.add(isel.org.x);
				end.add(isel.end.x);
				++i;
				if (isel.org.d != 0) { // exclusive
					for (int j = i; j < index.size(); ++j)
						org.addMax();
					if (i >= index.size()) // ensure at least one added
						org.addMax();
				}
				if (isel.end.d == 0) { // inclusive
					for (int j = i; j < index.size(); ++j)
						end.addMax();
					if (i >= index.size()) // ensure at least one added
						end.addMax();
				}
				break ;
			} else
				unreachable();
		}
		return tbl.tbl.getIndex(listToCommas(index)).rangefrac(org, end);
	}

	private double datafrac(List<List<String>> indexes) {
		// take the union of all the index fields
		// to ensure you don't use a field more than once
		List<String> flds = new ArrayList<String>();
		for (List<String> idx : indexes)
			addUnique(flds, idx);

		// frac = product of frac of each field
		double frac = 1;
		for (String fld : flds) {
			Double f = ffracs.get(fld);
			if (f != null)
				frac *= f;
		}
		return frac;
	}

	private double choose_primary(List<String> index) {
		// find index that satisfies required index with least cost
		double best_cost = IMPOSSIBLE;
		for (List<String> idx : theindexes) {
			if (!prefixed(idx, index, fixed()))
				continue;
			double cost = primarycost(idx);
			if (cost < best_cost) {
				primary = idx;
				best_cost = cost;
			}
		}
		return best_cost;
	}

	private double primarycost(List<String> idx) {
		double index_read_cost = ifracs.get(idx) * tbl.indexsize(idx);

		double data_frac = idx.containsAll(select_needs)
			? idx.containsAll(prior_needs) ? 0 : .5 * datafrac(list(idx))
			: datafrac(list(idx));

		double data_read_cost = data_frac * tbl.tbl.totalsize();
		return index_read_cost + data_read_cost;
	}

	private double choose_filter(double primary_cost) {
		List<List<String>> available = remove(possible, primary);
		if (nil(available))
			return primary_cost;
		double primary_index_cost = ifracs.get(primary)	* tbl.indexsize(primary);
		double best_cost = primary_cost;
		filter = new ArrayList<List<String>>();
		while (true) {
			List<String> best_filter = null;
			filter.add(0, null);
			for (List<String> idx : available) {
				filter.set(0, idx);
				double cost = costwith(filter, primary_index_cost);
				if (cost < best_cost) {
					best_cost = cost;
					best_filter = idx;
				}
			}
			if (best_filter == null)
				break; // can't reduce cost by adding another filter
			filter.set(0, best_filter);
		}
		filter.remove(0);
		return best_cost;
	}

	private final static int FILTER_KEYSIZE = 10;

	private double costwith(List<List<String>> filter, double primary_index_cost) {
		// PERF avoid copying filter
		List<List<String>> all = new ArrayList<List<String>>(filter);
		all.add(primary);

		double data_frac = primary.containsAll(select_needs)
			? includes(all, select_needs) ? 0 : .5 * datafrac(all)
			: datafrac(all);

		// approximate filter cost independent of order of filters
		double filter_cost = 0;
		for (List<String> f : filter) {
			double n = tbl.nrecords() * ifracs.get(f);
			filter_cost += n * tbl.keysize(f) // read cost
					+ n * FILTER_KEYSIZE * WRITE_FACTOR; // write cost
		}

		return data_frac * tbl.tbl.totalsize()
				+ primary_index_cost + filter_cost;
	}

	// determine whether a set of indexes includes a set of fields
	// like containsAll, but set of indexes is list of lists
	private boolean includes(List<List<String>> indexes, List<String> fields) {
		if (indexes.size() == 1)
			return indexes.get(0).containsAll(fields);
		outer: for (String field : fields) {
			for (List<String> index : indexes)
				if (index.contains(field))
					continue outer;
			return false; // field not found in any index
		}
	return true;
	}

	// end of optimize ==============================================

	@Override
	double nrecords() {
		return nrecs;
	}

	// get ----------------------------------------------------------

	@Override
	Row get(Dir dir) {
		if (first ) {
			first = false;
			iterate_setup();
		}
		if (rewound) {
			rewound = false;
			newrange = true;
			range_i = (dir == Dir.NEXT ? -1 : ranges.size()); // allow for ++
		}
		while (true) {
			if (newrange) {
				Keyrange range;
				do 	{
					range_i += (dir == Dir.NEXT ? 1 : -1);
					if (dir == Dir.NEXT ? range_i >= ranges.size()
							: range_i < 0)
						return null;
					range = Keyrange.intersect(sel, ranges.get(range_i));
				} while (range.isEmpty());
				source.select(source_index, range.org, range.end);
				newrange = false;
				}
			Row row;
			do {
				row = source.get(dir);
				++n_in;
			} while (row != null && !matches(row));
			if (row != null) {
				++n_out;
				return row;
			}
			newrange = true;
		}
	}

	private void iterate_setup() {
		processFilters();
		hdr = source.header();
		ranges = selects(source_index, iselects(source_index));
	}

	private void processFilters() {
		if (nil(filter))
			return;

		for (List<String> ix : filter) {
			HashSet<Long> newset = new HashSet<Long>();
			tbl.set_index(ix);
			for (Keyrange range : selects(ix, iselects(ix))) {
				for (source.select(ix, range.org, range.end);
						null != source.get(Dir.NEXT); )
					if (matches(ix, tbl.iter.cur().key)
							&& (f == null || f.contains(tbl.iter.keyadr())))
						newset.add(tbl.iter.keyadr());
			}
			f = newset;
		}
		tbl.set_index(source_index); // restore primary index

		// remove filter isels - no longer needed
		for (List<String> idx : filter)
			for (String fld : idx)
				isels.remove(fld);
	}

	private List<Keyrange> selects(List<String> index, List<Iselect> iselects) {
		// TODO Auto-generated method stub
		return null;
	}

	boolean matches(Row row) {
		// first check against filter
		if (f != null && !f.contains(tbl.iter.keyadr()))
			return false;

		// then check against isels
		// TODO: check keys before data (every other one)
		for (Map.Entry<String,Iselect> e : isels.entrySet()) {
			Iselect isel = e.getValue();
			ByteBuffer value = row.getraw(hdr, e.getKey());
			if (! isel.matches(value))
				return false;
		}
		// finally check remaining expressions
		row.setTransaction(tran);
		return expr.eval(hdr, row) == SuBoolean.TRUE;
	}

	private boolean matches(List<String> idx, Record key) {
		for (int i = 0; i < idx.size(); ++i)
			if (!isels.get(idx.get(i)).matches(key.getraw(i)))
				return false;
		return true;
	}

	// end of get ---------------------------------------------------

	@Override
	void rewind() {
		source.rewind();
		rewound = true;
	}

	@Override
	void select(List<String> index, Record from, Record to) {
		if (conflicting) {
			sel.setNone();
		} else if (prefix(source_index, index)) {
			sel.set(from, to);
		} else
			convert_select(index, from, to);
		rewound = true;
	}

	private void convert_select(List<String> index, Record from, Record to) {
		// TODO: could optimize for case where from == to
		if (from.equals(Record.MINREC) && to.equals(Record.MAXREC)) {
			sel.setAll();
			return ;
		}
		Record newfrom = new Record();
		Record newto = new Record();
		int si = 0; // source_index;
		int ri = 0; // index;
		SuValue fixval;
		while (ri < index.size()) {
			String sidx = source_index.get(si);
			String ridx = index.get(ri);
			if (si < source_index.size() && sidx.equals(ridx)) {
				int i = index.indexOf(ridx);
				newfrom.add(from.getraw(i));
				newto.add(to.getraw(i));
				++si;
				++ri;
			}
			else if (si < source_index.size()
					&& null != (fixval = getfixed(fix, sidx))) {
				newfrom.add(fixval);
				newto.add(fixval);
				++si;
			}
			else if (null != (fixval = getfixed(fix, ridx))) {
				int i = index.indexOf(ridx);
				if (fixval.compareTo(from.get(i)) < 0
						|| fixval.compareTo(to.get(i)) > 0)
					{
					// select doesn't match fixed so empty select
					sel.setNone();
					return ;
					}
				++ri;
			}
			else
				throw unreachable();
			}
		if (from.getraw(from.size() - 1).equals(Record.MAX_FIELD))
			newfrom.add(Record.MAX_FIELD);
		if (to.getraw(to.size() - 1) == Record.MAX_FIELD)
			newto.add(Record.MAX_FIELD);
		sel.set(newfrom, newto);
	}

	private static SuValue getfixed(List<Fixed> fixed, String field) {
		for (Fixed f : fixed)
			if (f.field.equals(field) && f.values.size() == 1)
				return f.values.get(0);
		return null;
	}

	@Override
	void setTransaction(Transaction tran) {
		this.tran = tran;
	}

	private static class Cmp implements Comparable<Cmp> {
		final String ident;
		final BinOp.Op op;
		final ByteBuffer value;
		final List<ByteBuffer> values;

		Cmp() {
			ident = null;
			op = null;
			value = null;
			values = null;
		}

		Cmp(String ident, BinOp.Op op, ByteBuffer value) {
			this.ident = ident;
			this.op = op;
			this.value = value;
			values = null;
		}

		Cmp(String ident, Record rec) {
			this.ident = ident;
			op = null;
			value = null;
			values = new ArrayList<ByteBuffer>();
			for (ByteBuffer buf : rec)
				values.add(buf);
		}

		public boolean equals(Cmp c) {
			return ident == c.ident && op == c.op && value.equals(c.value);
			// TODO what about values ?
		}

		public int compareTo(Cmp other) {
			return ident.compareTo(other.ident);
		}

		@Override
		public String toString() {
			return "Cmp " + ident + " " + op.name + valueToString(value)
					+ valuesToString(values);
		}
	}

	// Iselect ------------------------------------------------------

	enum IselType { RANGE, VALUES };
	private static class Iselect {
		IselType type = IselType.RANGE;
		// range
		Point org = new Point();
		Point end = new Point();
		// set
		List<ByteBuffer> values;

		Iselect() {
			org.x = MIN_FIELD;
			end.x = MAX_FIELD;
		}
		boolean matches(ByteBuffer value) {
			return type == IselType.RANGE ? inrange(value)
					: values.contains(value);
		}
		boolean inrange(ByteBuffer x) {
			int org_cmp = org.x.compareTo(x);
			if (org_cmp > 0 || (org_cmp == 0 && org.d != 0))
				return false;
			int end_cmp = end.x.compareTo(x);
			if (end_cmp < 0 || (end_cmp == 0 && end.d != 0))
				return false;
			return true;
		}
		void and_with(Iselect r) {
			if (type == IselType.RANGE && r.type == IselType.RANGE) {
				// both ranges
				if (r.org.compareTo(org) > 0)
					org = r.org;
				if (r.end.compareTo(end) < 0)
					end = r.end;
			} else if (type == IselType.VALUES && r.type == IselType.VALUES)
				// both sets
				values = intersect(values, r.values);
			else {
				// set and range
				if (type == IselType.VALUES) {
					org = r.org;
					end = r.end;
					r.values = values;
				}
				values = new ArrayList<ByteBuffer>();
				for (ByteBuffer x : r.values)
					if (inrange(x))
						values.add(x);
				org.x = MAX_FIELD;
				end.x = MIN_FIELD; // empty range
				type = IselType.VALUES;
			}
		}
		boolean all() {
			return org.x == MIN_FIELD && org.d == 0 && end.x == MAX_FIELD;
		}
		boolean none() {
			return org.compareTo(end) > 0 && values.size() == 0;
		}
		boolean one() {
			return org.equals(end) || values.size() == 1;
		}
		boolean equals(Iselect y) {
			return org.equals(y.org) && end.equals(y.end);
		}

		@Override
		public String toString() {
			return "Isel " + type + " " + org + " .. " + end
					+ valuesToString(values);
		}
	}
	private static class Point implements Comparable<Point> {
		ByteBuffer x;
		int d = 0;		// 0 if inclusive, +1 or -1 if exclusive

		public boolean equals(Point p) {
			return d == p.d && x.equals(p.x);
		}

		public int compareTo(Point p) {
			int cmp = x.compareTo(p.x);
			return cmp == 0 ? d - p.d : cmp;
		}

		@Override
		public String toString() {
			return "Point " + (x == null ? "null" : valueToString(x)) + ","	+ d;
		}
	}

	private static String valueToString(ByteBuffer value) {
		return value.equals(MIN_FIELD) ? "min"
				: value.equals(MAX_FIELD) ? "max"
				: SuValue.toString(value);
	}
	private static String valuesToString(List<ByteBuffer> values) {
		String s = "";
		if (values != null)
			for (ByteBuffer b : values)
				s += " " + valueToString(b);
		return s;
	}

}
