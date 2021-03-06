/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime.builtin;

import java.util.concurrent.TimeUnit;

import suneido.Suneido;
import suneido.runtime.Ops;
import suneido.runtime.Params;

public class Scheduled {

	@Params("ms, block")
	public static Object Delayed(Object ms, Object fn) {
		Suneido.schedule(new Run(fn),
				Ops.toInt(ms), TimeUnit.MILLISECONDS);
		return null;
	}

	private static class Run implements Runnable {
		private final Object fn;
		public Run(Object fn) {
			this.fn = fn;
		}
		@Override
		public void run() {
			Ops.call(fn);
		}
	}

}
