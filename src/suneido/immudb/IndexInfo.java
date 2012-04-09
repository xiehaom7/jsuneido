/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.immudb;

import static suneido.util.Util.commaSplitter;

import java.util.Arrays;

import javax.annotation.concurrent.Immutable;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;

/**
 * {@link BtreeInfo} plus columns
 */
@Immutable
class IndexInfo extends BtreeInfo {
	static final int NFIELDS = 5;
	final int[] columns;

	IndexInfo(int[] columns, BtreeInfo info) {
		super(info.root, info.rootNode, info.treeLevels, info.nnodes, info.totalSize);
		this.columns = columns;
	}

	IndexInfo(int[] columns, int root, int treeLevels, int nnodes, int totalSize) {
		super(root, treeLevels, nnodes, totalSize);
		this.columns = columns;
	}

	IndexInfo(Record rec, int i) {
		super(rec.getInt(i + 1), rec.getInt(i + 2), rec.getInt(i + 3),
				rec.getInt(i + 4));
		columns = convertColumns(rec.getString(i));
	}

	IndexInfo(IndexInfo info, BtreeNode rootNode) {
		super(rootNode.address(), rootNode, info.treeLevels, info.nnodes, info.totalSize);
		this.columns = info.columns;
	}

	private static int[] convertColumns(String s) {
		Iterable<String> cs = commaSplitter(s);
		int[] cols = new int[Iterables.size(cs)];
		int c = 0;
		for (String col : cs)
			cols[c++] = Integer.parseInt(col);
		return cols;
	}

	void addToRecord(RecordBuilder rb) {
		assert root != 0;
		rb.add(convertColumns(columns))
				.add(root).add(treeLevels).add(nnodes).add(totalSize);
	}

	private static String convertColumns(int[] cols) {
		if (cols.length == 0)
			return "";
		StringBuilder sb = new StringBuilder();
		for (int c : cols)
			sb.append(",").append(c);
		return sb.substring(1);
	}

	static void addToRecord(RecordBuilder rb, String columns, BtreeInfo info) {
		rb.add(columns).add(info.root).add(info.treeLevels).add(info.nnodes);
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("columns", Arrays.toString(columns))
			.add("root", root)
			.add("rootNode", rootNode)
			.add("treeLevels", treeLevels)
			.add("nnodes", nnodes)
			.add("totalSize", totalSize)
			.toString();
	}

	void check() {
		assert ! IntRefs.isIntRef(root);
	}

}
