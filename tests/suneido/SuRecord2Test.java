/* Copyright 2012 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SuRecord2Test {
	private static SuValue rule1 = new SuContainer(); // just need non-null SuValue
	private static SuValue rule2 = new SuContainer(); // just need non-null SuValue

	@Test
	public void test_put_get() {
		SuRecord2 r = new NoRules();
		assertEquals("", r.get("nonexistent"));
		r.put("a", 123);
		assertEquals(123, r.get("a"));
		r.put("b", 456);
		assertEquals(123, r.get("a"));
		assertEquals(456, r.get("b"));
	}
	static class NoRules extends SuRecord2 {
		@Override
		Object getRule(Object field) {
			return null;
		}
	}

	@Test
	public void test_no_dependencies() {
		SuRecord2 r = new NoDeps();
		assertEquals("result", r.get("a"));
	}
	static class NoDeps extends SuRecord2 {
		@Override
		Object getRule(Object field) {
			return rule1;
		}
		@Override
		Object executeRule(Object rule) {
			return "result";
		}
	}

	@Test
	public void test_dependencies() {
		WithRule r = new WithRule();
		r.put("a", 123);
		r.put("b", 456);
		assertEquals("123456", r.get("r"));
		assertEquals(1, r.count);
		assertEquals("123456", r.get("r"));
		assertEquals(1, r.count);
		r.put("b", "999");
		assertEquals(1, r.count);
		assertEquals("123999", r.get("r"));
		assertEquals(2, r.count);
		assertEquals("123999", r.get("r"));
		assertEquals(2, r.count);
	}
	static class WithRule extends NoDeps {
		int count = 0;

		@Override
		Object getRule(Object field) {
			return field.equals("r") ? rule1 : null;
		}
		@Override
		Object executeRule(Object rule) {
			++count;
			return get("a").toString() + get("b").toString();
		}
	}

	@Test
	public void test_chained_rules() {
		WithChainedRules r = new WithChainedRules();
		r.put("a", "xy");
		assertEquals("=x", r.get("r1"));
		assertEquals(1, r.count1);
		assertEquals(1, r.count2);
		r.put("a", "XY");
		assertEquals("=X", r.get("r1"));
		assertEquals(2, r.count1);
		assertEquals(2, r.count2);
		r.put("a", "XZ");
		assertEquals("=X", r.get("r1"));
		assertEquals(3, r.count2);
		assertEquals(2, r.count1);
	}
	// r1 => r2 => a
	static class WithChainedRules extends NoDeps {
		int count1 = 0;
		int count2 = 0;

		@Override
		Object getRule(Object field) {
			return field.equals("r1") ? rule1
					: field.equals("r2") ? rule2 : null;
		}
		@Override
		Object executeRule(Object rule) {
			if (rule == rule1) {
				++count1;
				return "=" + get("r2").toString();
			} else if (rule == rule2) {
				++count2;
				return get("a").toString().substring(0, 1); // first char
			} else
				throw new RuntimeException("unreachable");
		}
	}



}
