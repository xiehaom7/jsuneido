/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.util;

import java.util.Comparator;

import com.google.common.primitives.Ints;

public class IdentityComparator implements Comparator<Object> {

	@Override
	public int compare(Object o1, Object o2) {
		return Ints.compare(System.identityHashCode(o1), System.identityHashCode(o2));
	}

}
