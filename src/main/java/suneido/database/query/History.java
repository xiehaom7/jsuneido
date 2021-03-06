/* Copyright 2008 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.query;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import suneido.database.immudb.Dbpkg;
import suneido.database.immudb.HistoryIterator;
import suneido.database.immudb.Record;
import suneido.database.immudb.Transaction;

public class History extends Query {
	private final String tablename;
	private final suneido.database.immudb.Table tbl;
	private List<String> columns = null;
	private Header header = null;
	private List<List<String>> indexes;
	private List<List<String>> keys;
	private final HistoryIterator iter;

	History(Transaction tran, String tablename) {
		this.tablename = tablename;
		tbl = tran.ck_getTable(tablename);
		iter = tran.historyIterator(tbl.num());
	}

	@Override
	public String toString() {
		return "history(" + tablename + ")";
	}

	@Override
	List<String> columns() {
		if (columns  == null)
			columns = new ImmutableList.Builder<String>()
					.addAll(tbl.getColumns())
					.add("_action")
					.add("_date")
					.build();
		return columns;
	}

	@Override
	int columnsize() {
		return 10;
	}

	@Override
	public Header header() {
		if (header == null)
			header = new Header(
					ImmutableList.of(
						noFields, ImmutableList.of("_date", "_action"),
						noFields, tbl.getFields()),
					columns());
		return header;
	}

	@Override
	boolean singleDbTable() {
		return false;
	}

	@Override
	List<List<String>> indexes() {
		if (indexes == null)
			indexes = ImmutableList.of(ImmutableList.of("_date"));
		return indexes;
	}

	@Override
	public List<List<String>> keys() {
		if (keys == null)
			keys = ImmutableList.of(ImmutableList.of("_date"));
		return keys;
	}

	@Override
	double nrecords() {
		return 1000;
	}

	@Override
	double optimize2(List<String> index, Set<String> needs,
			Set<String> firstneeds, boolean is_cursor, boolean freeze) {
		return 100000;
	}

	@Override
	int recordsize() {
		return 100;
	}

	@Override
	public void rewind() {
		iter.rewind();
	}

	@Override
	void select(List<String> index, Record from, Record to) {
		iter.rewind();
	}

	@Override
	public void setTransaction(Transaction tran) {
	}

	@Override
	public Row get(Dir dir) {
		Record[] data = (dir == Dir.NEXT) ? iter.getNext() : iter.getPrev();
		if (data == null)
			return null;
		return new Row(Dbpkg.MIN_RECORD, data[0], Dbpkg.MIN_RECORD, data[1]);
	}

	@Override
	public void close() {
	}

}
