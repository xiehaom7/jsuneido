/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import static suneido.SuInternalError.unreachable;

import java.nio.ByteBuffer;
import java.util.Iterator;

import suneido.runtime.Ops;
import suneido.runtime.Pack;
import suneido.runtime.Pack.Tag;
import suneido.util.ByteBuffers;
import suneido.util.CommaStringBuilder;

/**
 * Abstract base class for {@link BufRecord} and {@link ArrayRecord}.
 * @see RecordBuilder
 */
public abstract class Record
		implements suneido.Packable, Comparable<Record>, Iterable<ByteBuffer> {
	static final Record EMPTY = new RecordBuilder().bufRec();
	public static final ByteBuffer MIN_FIELD =
			ByteBuffer.allocate(0);
	public static final ByteBuffer MAX_FIELD =
			ByteBuffer.allocate(1).put(0, (byte) 0x7f).asReadOnlyBuffer();

	/** @return The number of fields in the Record */
	public abstract int size();

	abstract int packSize();

	public abstract ByteBuffer getBuffer();

	protected Record() {
	}

	static BufRecord from(ByteBuffer buf) {
		return new BufRecord(buf);
	}

	static BufRecord from(int address, ByteBuffer buf) {
		return new DataRecord(address, buf, 0);
	}

	static BufRecord from(ByteBuffer buf, int bufpos) {
		return new BufRecord(buf, bufpos);
	}

	static BufRecord from(int address, ByteBuffer buf, int bufpos) {
		return new DataRecord(address, buf, bufpos);
	}

	static BufRecord from(Storage stor, int adr) {
		return new DataRecord(stor, adr);
	}

	abstract ByteBuffer fieldBuffer(int i);

	abstract int fieldLength(int i);

	abstract int fieldOffset(int i);

	/** Number of bytes e.g. for storing */
	public int bufSize() {
		return packSize();
	}

	@Override
	public int packSize(int nest) {
		return packSize();
	}

	@Override
	public int hashCode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equals(Object that) {
		if (this == that)
			return true;
		if (that instanceof Record)
			return 0 == compareTo((Record) that);
		return false;
	}

	@Override
	public int compareTo(Record that) {
		int len1 = this.size();
		int len2 = that.size();
		int n = Math.min(len1, len2);
		for (int i = 0; i < n; ++i) {
			int cmp = compare1(this, that, i);
			if (cmp != 0)
				return cmp;
		}
		return len1 - len2;
	}

	private static int compare1(Record x, Record y, int i) {
		return compare1(
				x.fieldBuffer(i), x.fieldOffset(i), x.fieldLength(i),
				y.fieldBuffer(i), y.fieldOffset(i), y.fieldLength(i));
	}

	static int compare1(
			ByteBuffer buf1, int off1, int len1,
			ByteBuffer buf2, int off2, int len2) {
		int n = Math.min(len1, len2);
		for (int i = 0; i < n; ++i) {
			int cmp = (buf1.get(off1 + i) & 0xff) - (buf2.get(off2 + i) & 0xff);
			if (cmp != 0)
				return cmp;
		}
		return len1 - len2;
	}

	public Object get(int i) {
		if (i >= size())
			return "";
		return get(fieldBuffer(i), fieldOffset(i), fieldLength(i));
	}

	private static Object get(ByteBuffer buf, int off, int len) {
		if (len == 0)
			return "";
		byte x = buf.get(off);
		if (x == 'c' || x == 's' || x == 'l')
			return Record.from(buf, off);
		if (x == Tag.STRING) // avoid duplicate for strings
			return ByteBuffers.bufferToString(buf, off + 1, len - 1);
		ByteBuffer b = buf.duplicate();
		b.position(off);
		b.limit(off + len);
		return Pack.unpack(b);
		//PERF change unpack to take buf,i,n and not mutate to eliminate duplicate()
	}

	public int getInt(int i) {
		long n = getLong(i);
		if (n < Integer.MIN_VALUE || Integer.MAX_VALUE < n)
			throw new RuntimeException("Record getInt value out of range");
		return (int) n;
	}

	long getLong(int i) {
		ByteBuffer b = fieldBuffer(i).duplicate();
		int off = fieldOffset(i);
		b.position(off);
		b.limit(off + fieldLength(i));
		return Pack.unpackLong(b);
		//PERF change unpackLong to take buf,i,n and not mutate to eliminate duplicate
	}

	public String getString(int i) {
		return (String) get(i);
	}

	@Override
	public String toString() {
		CommaStringBuilder sb = new CommaStringBuilder("[");
		for (int i = 0; i < size(); ++i)
			sb.add(getRaw(i).equals(MAX_FIELD) ? "MAX" : Ops.display(get(i)));
		return sb.append("]").toString();
	}

	@Override
	public Iterator<ByteBuffer> iterator() {
		return new Iter();
	}

	private class Iter implements Iterator<ByteBuffer> {
		int i = 0;

		@Override
		public boolean hasNext() {
			return i < size();
		}

		@Override
		public ByteBuffer next() {
			return getRaw(i++);
		}

		@Override
		public void remove() {
			throw unreachable();
		}
	}

	public ByteBuffer getRaw(int i) {
		if (i >= size())
			return ByteBuffers.EMPTY_BUF;
		return ByteBuffers.slice(fieldBuffer(i), fieldOffset(i), fieldLength(i));
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	public Object getRef() {
		return getBuffer();
	}

	public int address() {
		return 0;
	}

	int dataSize() {
		int dataSize = 0;
		for (int i = 0; i < size(); ++i)
			dataSize += fieldLength(i);
		return dataSize;
	}

}
