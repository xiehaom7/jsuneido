/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.language.builtin;

import suneido.language.*;

public class QueryScanner extends Scanner {

	public QueryScanner(String s) {
		super(s);
		lexer.ignoreCase();
	}

	public static final BuiltinClass clazz = new BuiltinClass() {
		@Override
		public QueryScanner newInstance(Object... args) {
			args = Args.massage(FunctionSpec.string, args);
			return new QueryScanner(Ops.toStr(args[0]));
		}
	};

	// not quite correct since this will include language only keywords
	// e.g. dll stuff
	@Override
	protected boolean isKeyword() {
		return lexer.getKeyword() != Token.NIL;
	}

}
