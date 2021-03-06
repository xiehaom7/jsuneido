/* Copyright 2008 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.query.expr;

import static suneido.util.Util.displayListToParens;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import suneido.database.immudb.Record;
import suneido.database.immudb.RecordBuilder;
import suneido.database.query.Header;
import suneido.database.query.Row;
import suneido.runtime.Ops;

public class In extends Expr {
	public final Expr expr;
	private final Set<Object> values;
	public final Record packed;
	private boolean isTerm = false; // valid for isTermFields
	private List<String> isTermFields = null;

	public In(Expr expr, List<Object> values) {
		this.expr = expr;
		this.values = toSet(values);
		this.packed = convert(this.values);
	}

	private static Set<Object> toSet(List<Object> values) {
		return (values == null)
				? Collections.emptySet() : ImmutableSet.copyOf(values);
	}

	private static Record convert(Set<Object> values) {
		RecordBuilder rb = new RecordBuilder();
		for (Object value : values)
			rb.add(value);
		return rb.build();
	}

	private In(Expr expr, Set<Object> values, Record packed) {
		this.expr = expr;
		this.values = values;
		this.packed = packed;
	}

	@Override
	public String toString() {
		return expr + " in " + displayListToParens(values);
	}

	@Override
	public List<String> fields() {
		return expr.fields();
	}

	@Override
	public Expr fold() {
		if (values.isEmpty())
			return Constant.FALSE;
		Expr new_expr = expr.fold();
		if (new_expr instanceof Constant)
			return Constant.valueOf(eval2(((Constant) new_expr).value));
		return new_expr == expr ? this : new In(new_expr, values, packed);
	}

	// see also BinOp
	@Override
	public boolean isTerm(List<String> fields) {
		if (! fields.equals(isTermFields)) {
			isTerm = isTerm2(fields); // cache
			isTermFields = fields;
		}
		return isTerm;
	}

	private boolean isTerm2(List<String> fields) {
		return expr.isField(fields);
	}

	@Override
	public Object eval(Header hdr, Row row) {
		// only use raw comparison if isTerm has been used (by Select)
		// NOTE: do NOT want to use raw for Extend because of rule issues
		if (isTerm && hdr.fields().equals(isTermFields)) {
			Identifier id = (Identifier) expr;
			ByteBuffer value = row.getraw(hdr, id.ident);
			for (ByteBuffer v : packed)
				if (v.equals(value))
					return Boolean.TRUE;
			return Boolean.FALSE;
		} else {
			Object x = expr.eval(hdr, row);
			return eval2(x);
		}
	}

	private Object eval2(Object x) {
		for (Object y : values)
			if (Ops.is(x, y))
				return Boolean.TRUE;
		return Boolean.FALSE;
	}

	@Override
	public Expr rename(List<String> from, List<String> to) {
		Expr new_expr = expr.rename(from, to);
		return new_expr == expr ? this : new In(new_expr, values, packed);
	}

	@Override
	public Expr replace(List<String> from, List<Expr> to) {
		Expr new_expr = expr.replace(from, to);
		return new_expr == expr ? this : new In(new_expr, values, packed);
	}

	@Override
	public boolean cantBeNil(List<String> fields) {
		return isTerm(fields) && eval2("") == Boolean.FALSE;
	}
}
