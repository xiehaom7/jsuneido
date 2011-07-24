/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.immudb;

import javax.annotation.concurrent.NotThreadSafe;

import suneido.immudb.DbHashTrie.*;
import suneido.immudb.UpdateTransaction.Conflict;

/**
 * Normally immutable persistent trees are "updated" by copying & updating
 * nodes all the way up to the root (path copying).
 * To avoid writing so many nodes to disk, we save "redirections".
 * e.g. if we "update" a leaf, instead of updating its parents
 * we just "redirect" the leaf's old address to its new address.
 * <p>
 * Note: Redirections are stored in {@link DbHashTrie}
 * so it can't use this optimization.
 */
@NotThreadSafe
public class Redirects {
	private final DbHashTrie original;
	private DbHashTrie redirs;

	public Redirects(DbHashTrie redirs) {
		original = redirs;
		this.redirs = redirs;
	}

	public void put(int from, int to) {
		assert ! IntRefs.isIntRef(from);
		redirs = redirs.with(new IntEntry(from, to));
	}

	public int get(int from) {
		Entry e = redirs.get(from);
		return e == null ? from : ((IntEntry) e).value;
	}

	public int store(Translator translator) {
		return redirs.store(translator);
	}

	public void print() {
		redirs.print();
	}

	DbHashTrie redirs() {
		return redirs;
	}

	public void merge(DbHashTrie current) {
		if (current == original)
			return; // no concurrent changes to merge

		Proc proc = new Proc(original, current);
		redirs.traverseChanges(proc);
		redirs = proc.merged;
	}

	private static class Proc implements DbHashTrie.Process {
		private final DbHashTrie original;
		private final DbHashTrie current;
		private DbHashTrie merged;

		public Proc(DbHashTrie original, DbHashTrie current) {
			this.original = original;
			this.current = current;
			merged = current;
		}

		@Override
		public void apply(Entry e) {
			int adr = ((IntEntry) e).key;
			if (original.get(adr) != current.get(adr))
				throw conflict;
			merged = merged.with(e);
		}
	}

	private static final Conflict conflict =
			new Conflict("concurrent index node modification");

}
