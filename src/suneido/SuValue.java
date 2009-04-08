package suneido;

import java.nio.ByteBuffer;

import suneido.language.Ops;
import suneido.language.SuClass;

/**
 * Base class for Suneido data types:
 * @see SuContainer
 * @see SuRecord
 * @see SuClass
 * @author Andrew McKinlay
 * <p><small>Copyright 2008 Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.</small></p>
 */
public abstract class SuValue implements Packable {

	@Override
	public abstract String toString();

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object other);

	public int hashCode(int nest) {
		return hashCode();
	}

	public abstract Object invoke(String method, Object... args);

	public SuException unknown_method(String method) {
		return new SuException("unknown method " + typeName() + "." + method);
	}

	public String typeName() {
		return Ops.typeName(this);
	}

	public Object get(Object member) {
		throw new SuException(typeName() + " does not support get");
	}

	public void put(Object member, Object value) {
		throw new SuException(typeName() + " does not support put");
	}

	public int packSize() {
		return packSize(0);
	}

	public int packSize(int nest) {
		throw new SuException(typeName() + " cannot be stored");
	}

	public void pack(ByteBuffer buf) {
		throw new SuException(typeName() + " cannot be stored");
	}

}
