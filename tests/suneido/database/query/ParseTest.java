package suneido.database.query;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import suneido.SuException;
import suneido.database.TestBase;
import suneido.database.Transaction;

//TODO parse object constants

public class ParseTest extends TestBase {
	@Test
	public void test_parse() {
		makeTable();
		makeTable("test2", "x", "y");
		makeTable("compat", "a", "b");
		makeTable("joinable", "x", "y", "a");
		Request.execute(serverData, "view myview = test project a,b");
		Request.execute(serverData, "sview myview2 = test extend x=1");

		test("test");
		test("test SORT a,b");
		test("test SORT REVERSE a,b");
		test("test RENAME a to aa, b to bb");
		test("test RENAME a to aa RENAME b to bb");
		test("test RENAME a to aa, b to bb SORT a,b");
		test("test PROJECT a,b");
		test("test PROJECT b");
		test("test REMOVE b", "test PROJECT a");
		test("test REMOVE a", "test PROJECT b");
		test("(test TIMES test2)");
		test("(test MINUS compat)");
		test("(test UNION compat)");
		test("(test INTERSECT compat)");
		test("history(test)");
		test("test JOIN joinable", "(test JOIN 1:n on (a) joinable)");
		test("test LEFTJOIN joinable", "(test LEFTJOIN 1:n on (a) joinable)");
		test("test SUMMARIZE b, count, total a, x = max b",
			"test SUMMARIZE (b) count = count, total_a = total a, x = max b");
		test("test WHERE not a");
		test("test WHERE (a * 5)");
		test("test WHERE (a-5)", "test WHERE (a - 5)");
		test("test WHERE (a- -5)", "test WHERE (a - -5)");
		test("test WHERE (a+5)", "test WHERE (a + 5)");
		test("test WHERE (a >> 2)");
		test("test WHERE (a > 5)");
		test("test WHERE (a = b)", "test WHERE (a is b)");
		test("test WHERE (a & 4)");
		test("test WHERE (a ^ 2)");
		test("test WHERE (a | 1)");
		test("test WHERE (a and b)");
		test("test WHERE (a or 'a\\x72\\163z\\'\\?')", "test WHERE (a or \"arsz'\\\\?\")");
		test("joinable WHERE (a and x and y)");
		test("test WHERE (a or b)");
		test("joinable WHERE (a or y or x)");
		test("test WHERE (a ? b : 5)");
		test("test WHERE a in (2,3,4)");
		test("test EXTEND Z, x = 12, y = (b + a), f = fn(), g = fn(1), h = fn(1,2,3)");
		test("DELETE test WHERE (a is 5)");
		test("UPDATE test SET a=5, b=3");
		test("INSERT [b: 6, a: 4] INTO test");
		test("test WHERE (a is #20081216.1523)");
		test("test WHERE (a is #20081216.152301)");
		test("test WHERE (a is #20081216.152301234)");
		test("myview", "test PROJECT a,b");
		test("myview2", "test EXTEND x = 1");
		test("test union myview extend x = 1", "(test UNION test PROJECT a,b) EXTEND x = 1");
	}

	private void test(String s) {
		test(s, s);
	}
	private void test(String s, String expect) {
		Transaction t = db.readonlyTran();
		try {
			assertEquals(s, expect,
					CompileQuery.parse(t, serverData, s).toString());
		} finally {
			t.complete();
		}
	}

	private void makeTable(String tablename, String... columns) {
		db.addTable(tablename);
		for (String column : columns)
			db.addColumn(tablename, column);
		db.addIndex(tablename, columns[0], true);
	}

	@Test(expected = SuException.class)
	public void lexer_error() {
		CompileQuery.parse(serverData, "test where x = 1e~3");
	}

	@Test(expected = SuException.class)
	public void queryEof() {
		CompileQuery.parse(serverData, "test 123");
	}

	@Test(expected = SuException.class)
	public void exprEof() {
		CompileQuery.expr("x + 1 y");
	}

}
