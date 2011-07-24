/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.immudb;

import java.nio.ByteBuffer;

import suneido.util.IntArrayList;

/**
 * Abstract base class for store-able nodes {@link BtreeDbMemNode} and
 * {@link BtreeMemNode}.
 */
public abstract class BtreeStoreNode extends BtreeNode {

	protected BtreeStoreNode(int level) {
		super(level);
	}

	@Override
	public int store(Tran tran) {
		translate(tran);
		int adr = tran.stor.alloc(length());
		ByteBuffer buf = tran.stor.buffer(adr);
		pack(buf);
		return adr;
	}

	/** convert data record addresses and node pointers */
	abstract protected void translate(Tran tran);

	public int length() {
		int datasize = 0;
		for (int i = 0; i < size(); ++i)
			datasize += length(i);
		return RecordBuilder.length(size(), datasize);
	}

	abstract protected int length(int i);

	public void pack(ByteBuffer buf) {
		RecordBuilder.packHeader(buf, length(), getLengths());
		for (int i = size() - 1; i >= 0; --i)
			pack(buf, i);
	}

	protected IntArrayList getLengths() {
		IntArrayList lens = new IntArrayList(size());
		for (int i = 0; i < size(); ++i)
			lens.add(length(i));
		return lens;
	}

	abstract protected void pack(ByteBuffer buf, int i);

	protected Record translate(Tran tran, Record rec) {
		boolean translate = false;

		int dref = dataref(rec);
		if (dref != 0) {
			dref = tran.redir(dref);
			if (IntRefs.isIntRef(dref)) {
				dref = tran.getAdr(dref);
				assert dref != 0;
				translate = true;
			}
		}

		int ptr = 0;
		if (level > 0) {
			int ptr1 = ptr = pointer(rec);
			if (! IntRefs.isIntRef(ptr1))
				ptr = tran.redir(ptr1);
			if (IntRefs.isIntRef(ptr))
				ptr = tran.getAdr(ptr);
			assert ptr != 0;
			assert ! IntRefs.isIntRef(ptr) : "pointer " + ptr1 + " => " + (ptr & 0xffffffffL);
			if (ptr1 != ptr)
				translate = true;
			//TODO if redirections are unique then we can remove this one
		}

		if (! translate)
			return rec;

		int prefix = rec.size() - (level > 0 ? 2 : 1);
		RecordBuilder rb = new RecordBuilder().addPrefix(rec, prefix);
		if (dref == 0)
			rb.add("");
		else
			rb.add(dref);
		if (level > 0)
			rb.add(ptr);
		return rb.build();
	}

	private int dataref(Record rec) {
		int size = rec.size();
		int i = size - (level > 0 ? 2 : 1);
		Object x = rec.get(i);
		if (x.equals(""))
			return 0;
		return ((Number) x).intValue();
	}

}
