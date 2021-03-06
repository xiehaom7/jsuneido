/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime;

public final class SuClosure0 extends SuClosure {

	public SuClosure0(Object block, Object self, Object[] locals) {
		super(block, self, locals);
	}

	@Override
	public Object call0() {
		return wrapped.eval(self, locals);
	}

	@Override
	public Object eval0(Object newSelf) {
		return wrapped.eval(newSelf, locals);
	}

}
