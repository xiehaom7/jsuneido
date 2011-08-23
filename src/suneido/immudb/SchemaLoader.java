package suneido.immudb;

import static suneido.immudb.Bootstrap.indexColumns;

import java.util.Collections;
import java.util.List;

import suneido.immudb.Bootstrap.TN;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Load the database schema into memory at startup.
 * Used by Database.open
 */
class SchemaLoader {
	private final ReadTransaction t;

	static Tables load(ReadTransaction t) {
		SchemaLoader sl = new SchemaLoader(t);
		return sl.load();
	}

	private SchemaLoader(ReadTransaction t) {
		this.t = t;
	}

	private Tables load() {
		Btree tablesIndex = t.getIndex(TN.TABLES, indexColumns[TN.TABLES]); // table
		Btree columnsIndex = t.getIndex(TN.COLUMNS, indexColumns[TN.COLUMNS]); // table, column
		Btree indexesIndex = t.getIndex(TN.INDEXES, indexColumns[TN.INDEXES]); // table, columns

		TablesReader tr = new TablesReader(tablesIndex);
		ColumnsReader cr = new ColumnsReader(columnsIndex);
		IndexesReader ir = new IndexesReader(indexesIndex);
		Tables.Builder tsb = new Tables.Builder();
		while (true) {
			Record tblrec = tr.next();
			if (tblrec == null)
				break;
			int tblnum = tblrec.getInt(Table.TBLNUM);
			Columns columns = cr.next(tblnum);
			Indexes indexes = ir.next();
			Table table = new Table(tblrec, columns, indexes);
			tsb.add(table);
		}
		return tsb.build();
	}

	private class TablesReader {
		Btree.Iter iter;

		TablesReader(Btree tablesIndex) {
			iter = tablesIndex.iterator();
		}
		Record next() {
			iter.next();
			if (iter.eof())
				return null;
			Record key = iter.cur();
			return recordFromSlot(key);
		}
	}

	static final ImmutableList<Column> noColumns = ImmutableList.of();

	private class ColumnsReader {
		Btree.Iter iter;
		Column next;
		List<Column> list = Lists.newArrayList();

		ColumnsReader(Btree columnsIndex) {
			iter = columnsIndex.iterator();
			iter.next();
			next = column(iter.cur());
		}
		Columns next(int tblnum) {
			if (next == null || next.tblnum > tblnum)
				return new Columns(noColumns);
			while (true) {
				list.add(next);
				iter.next();
				if (iter.eof())
					break;
				Column prev = next;
				next = column(iter.cur());
				if (prev.tblnum != next.tblnum) {
					Columns cols = columns();
					list = Lists.newArrayList();
					return cols;
				}
			}
			next = null;
			return columns();
		}
		Column column(Record key) {
			return new Column(recordFromSlot(key));
		}
		Columns columns() {
			Collections.sort(list);
			return new Columns(ImmutableList.copyOf(list));
		}
	}

	private class IndexesReader {
		Btree.Iter iter;
		Index next;
		ImmutableList.Builder<Index> list = ImmutableList.builder();

		IndexesReader(Btree indexesIndex) {
			iter = indexesIndex.iterator();
			iter.next();
			next = index();
		}
		Indexes next() {
			if (next == null)
				return null;
			while (true) {
				list.add(next);
				iter.next();
				if (iter.eof())
					break;
				Index prev = next;
				next = index();
				if (prev.tblnum != next.tblnum) {
					Indexes result = new Indexes(list.build());
					list = ImmutableList.builder();
					return result;
				}
			}
			next = null;
			return new Indexes(list.build());
		}
		private Index index() {
			return new Index(recordFromSlot(iter.cur()));
		}
	}

	private Record recordFromSlot(Record slot) {
		int adr = Btree.getAddress(slot);
		return t.getrec(adr);
	}

}
