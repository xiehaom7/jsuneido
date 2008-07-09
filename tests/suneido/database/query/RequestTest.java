package suneido.database.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static suneido.database.Database.theDB;

import org.junit.Test;

import suneido.database.TestBase;

public class RequestTest extends TestBase {
	@Test
	public void test() {
		theDB = db;
		String schema = "(a,b,c) key(a)";
		Request.execute("create test " + schema);
		assertEquals(schema, db.schema("test"));

		Request.execute("ensure test (c,d,e) KEY(a) INDEX(b,c)");
		schema = "(a,b,c,d,e) key(a) index(b,c)";
		assertEquals(schema, db.schema("test"));

		String extra = " index(c) in other(cc)";
		Request.execute("alter test create" + extra);
		assertEquals(schema + extra, db.schema("test"));

		Request.execute("ALTER test DROP index(c)");
		assertEquals(schema, db.schema("test"));

		Request.execute("alter test rename b to bb");
		schema = "(a,bb,c,d,e) key(a) index(bb,c)";
		assertEquals(schema, db.schema("test"));

		Request.execute("alter test drop (d,e)");
		schema = "(a,bb,c) key(a) index(bb,c)";
		assertEquals(schema, db.schema("test"));

		Request.execute("RENAME test TO tmp");
		assertEquals(schema, db.schema("tmp"));
		assertNull(db.getTable("test"));

		Request.execute("drop tmp");
		assertNull(db.getTable("tmp"));
	}
}
