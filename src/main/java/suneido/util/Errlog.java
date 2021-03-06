/* Copyright 2014 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Errlog {
	private static Supplier<String> extra = () -> "";
	private static AtomicInteger count = new AtomicInteger();
	private static final int LIMIT = 1000;

	public static void setExtra(Supplier<String> extra) {
		Errlog.extra = extra;
	}

	public static void uncounted(String s) {
		log_uncounted("", s, null);
	}

	public static void bare(String s) {
		log("", s, null);
	}

	public static void info(String s) {
		log("INFO", s, null);
	}

	public static void warn(String s) {
		log("WARNING", s, null);
	}

	public static void error(String s) {
		error(s, null);
	}

	public static void error(String s, Throwable e) {
		log("ERROR", s, e);
	}

	public static void fatal(String s) {
		fatal(s, null);
	}

	public static void fatal(String s, Throwable e) {
		Util.interruptableSleep(10); // give threads a chance to exit gracefully
		log("FATAL ERROR", s, e);
		System.exit(-1);
	}

	/** like assert but just logs, doesn't throw */
	public static void verify(boolean arg, String msg) {
		if (! arg)
			Errlog.error(msg);
	}

	/** run the given function, catching and logging any errors */
	public static void run(Runnable fn) {
		try {
			fn.run();
		} catch (Throwable e) {
			Errlog.error("", e);
		}
	}

	private static synchronized void log(String prefix, String s, Throwable e) {
		if (count.get() > LIMIT)
			return;
		if (count.getAndAdd(1) == LIMIT) {
			s = "too many errors, stopping logging";
			e = null;
		}
		log_uncounted(prefix, s, e);
	}

	private static void log_uncounted(String prefix, String s, Throwable e) {
		if (! prefix.isEmpty())
			prefix = prefix + ": ";
		String sid = extra.get();
		if (! sid.isEmpty())
			sid = sid + " ";
		System.out.println(sid + prefix +
				s + (s.isEmpty() ? "" : " ") +
				(e == null ? "" : e));
		try (PrintWriter pw = new PrintWriter(new FileWriter("error.log", true))) {
			pw.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
				.append(" ")
				.append(sid)
				.append(prefix + s)
				.println();
			if (e != null) {
				e.printStackTrace(pw);
			}
		} catch (IOException e2) {
			System.err.println("can't write to error.log " + e2);
		}
	}

	/** for tests */
	public static int count() {
		return count.get();
	}

}
