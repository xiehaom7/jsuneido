package suneido.database;

import static suneido.SuException.verifyEquals;
import static suneido.database.Database.theDB;

import java.io.File;
import java.io.IOException;
import java.util.List;

import suneido.SuException;
import suneido.database.query.Request;

public class DbCompact {
	private final String dbFilename;
	private Database oldDB;
	private Transaction rt;

	public static int compact(String db_filename) {
		return new DbCompact(db_filename).process();
	}

	private DbCompact(String dbFilename) {
		this.dbFilename = dbFilename;
	}

	private int process() {
		File tmpfile;
		try {
			tmpfile = File.createTempFile("sudb", null, new File("."));
		} catch (IOException e) {
			throw new SuException("rebuild failed", e);
		}
		oldDB = new Database(dbFilename, Mode.READ_ONLY);
		theDB = new Database(tmpfile, Mode.CREATE);

		int n = copy();

		oldDB.close();
		theDB.close();
		theDB = null;

		File dbfile = new File(dbFilename);
		File bakfile = new File(dbFilename + ".bak");
		bakfile.delete();
		dbfile.renameTo(bakfile);
		tmpfile.renameTo(dbfile);

		return n;
	}

	private int copy() {
		rt = oldDB.readonlyTran();
		theDB.loading = true;
		copySchema();
		return copyData() + 1; // + 1 for views
	}

	private void copySchema() {
		copyTable("views");
		BtreeIndex bti = rt.getBtreeIndex(Database.TN.TABLES, "tablename");
		BtreeIndex.Iter iter = bti.iter(rt).next();
		for (; !iter.eof(); iter.next()) {
			Record r = rt.input(iter.keyadr());
			String tablename = r.getString(Table.TABLE);
			if (!Schema.isSystemTable(tablename))
				createTable(tablename);
		}
	}

	private void createTable(String tablename) {
		Request.execute("create " + tablename
				+ oldDB.getTable(tablename).schema());
		verifyEquals(oldDB.getTable(tablename).schema(), theDB.getTable(tablename).schema());
	}

	private int copyData() {
		BtreeIndex bti = rt.getBtreeIndex(Database.TN.TABLES, "tablename");
		BtreeIndex.Iter iter = bti.iter(rt).next();
		int n = 0;
		for (; !iter.eof(); iter.next()) {
			Record r = rt.input(iter.keyadr());
			String tablename = r.getString(Table.TABLE);
			if (!Schema.isSystemTable(tablename)) {
				copyTable(tablename);
				++n;
			}
		}
		return n;
	}

	// TODO build indexes after outputting all the data
	private void copyTable(String tablename) {
		Table table = rt.ck_getTable(tablename);
		List<String> fields = table.getFields();
		boolean squeeze = DbDump.needToSqueeze(fields);
		Index index = table.indexes.first();
		BtreeIndex bti = rt.getBtreeIndex(index);
		BtreeIndex.Iter iter = bti.iter(rt).next();
		int i = 0;
		Transaction wt = theDB.readwriteTran();
		for (; !iter.eof(); iter.next()) {
			Record r = rt.input(iter.keyadr());
			if (squeeze)
				r = DbDump.squeezeRecord(r, fields);
			Data.add_any_record(wt, tablename, r);
			if (++i % 100 == 0) {
				wt.ck_complete();
				wt = theDB.readwriteTran();
			}
		}
		wt.ck_complete();
	}

	public static void main(String[] args) {
		int n = DbCompact.compact("suneido.db");
		System.out.println("Compacted " + n + " tables");
	}

}
