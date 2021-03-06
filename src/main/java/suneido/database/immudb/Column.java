/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import suneido.util.Immutable;

import suneido.database.immudb.Bootstrap.TN;

/**
 * Wrapper for a record from the columns table.
 * Used by {@link Columns}.
 */
@Immutable
class Column implements Comparable<Column> {
	private static final int TBLNUM = 0, FLDNUM = 1, COLUMN = 2;
	final int tblnum;
	final int field;
	final String name;

	Column(int tblnum, int field, String column) {
		this.tblnum = tblnum;
		this.name = column;
		this.field = field;
	}

	Column(Record record) {
		tblnum = record.getInt(TBLNUM);
		field = record.getInt(FLDNUM);
		name = record.getString(COLUMN);
	}

	DataRecord toRecord() {
		return toRecord(tblnum, field, name);
	}

	static DataRecord toRecord(int tblnum, int field, String column) {
		DataRecord r = new RecordBuilder().add(tblnum).add(field).add(column).build();
		r.tblnum(TN.COLUMNS);
		return r;
	}

	@Override
	public int compareTo(Column other) {
		return field - other.field;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof Column))
			return false;
		return field == ((Column) other).field;
	}

	@Override
	public int hashCode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return name + ":" + field;
	}

}
