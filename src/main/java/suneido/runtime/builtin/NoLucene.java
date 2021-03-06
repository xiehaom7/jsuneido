/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime.builtin;

import suneido.SuException;
import suneido.runtime.BuiltinClass;
import suneido.runtime.Params;

public class NoLucene extends BuiltinClass {
	public static final NoLucene singleton = new NoLucene();

	private NoLucene() {
		super(NoLucene.class);
	}

	@Override
	protected Object newInstance(Object... args) {
		throw new SuException("cannot create instances of Lucene");
	}

	@Params("dir")
	public static Object AvailableQ(Object self, Object a) {
		return false;
	}

}