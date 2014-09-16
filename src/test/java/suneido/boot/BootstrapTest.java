/* Copyright 2014 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.boot;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import suneido.util.LineBufferingByteConsumer;

/**
 * Test to ensure that bootstrap process works under JUnit.
 *
 * @author Victor Schappert
 * @since 20140814
 */
@RunWith(Parameterized.class)
public class BootstrapTest {

	private static final class LineCollector extends OutputStream {

		public final ArrayList<String> lines = new ArrayList<String>();
		private final LineBufferingByteConsumer lbbc = new LineBufferingByteConsumer(
				(CharSequence cs) -> {
					if (null != cs) {
						lines.add(cs.toString());
					}
				});

		public boolean has(String pattern) {
			final Pattern pattern_ = Pattern.compile(pattern);
			for (String line : lines) {
				if (pattern_.matcher(line).find()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void close() throws IOException {
			super.close(); // Will flush
			lbbc.close();
		}

		@Override
		public void write(int arg0) throws IOException {
			byte[] b = new byte[1];
			b[0] = (byte) arg0;
			lbbc.accept(b, 1);
		}

	}

	private static PrintStream initialSystemDotOut;
	private static String initialSkipBoot;
	private LineCollector collector;
	private final boolean skipBoot;
	private final String debugOption;

	public BootstrapTest(boolean skipBoot, String debugOption) {
		this.skipBoot = skipBoot;
		this.debugOption = debugOption;
	}

	@BeforeClass
	public static void setupAllTests() {
		initialSystemDotOut = System.out;
		initialSkipBoot = System.getProperty(Bootstrap.SKIP_BOOT_PROPERTY_NAME);
	}

	@AfterClass
	public static void teardownAllTests() {
		if (System.out != initialSystemDotOut) {
			System.setOut(initialSystemDotOut);
			System.err.println("ERROR: a test failed to restore System.out");
		}
	}

	@Before
	public void setUpOneTest() throws IOException {
		collector = new LineCollector();
		System.setOut(new PrintStream(collector));
		if (skipBoot) {
			System.setProperty(Bootstrap.SKIP_BOOT_PROPERTY_NAME, "true");
		} else {
			System.clearProperty(Bootstrap.SKIP_BOOT_PROPERTY_NAME);
		}
	}

	@After
	public void teardownOneTest() throws IOException {
		System.setOut(initialSystemDotOut);
		if (null == initialSkipBoot) {
			System.clearProperty(Bootstrap.SKIP_BOOT_PROPERTY_NAME);
		} else {
			System.setProperty(Bootstrap.SKIP_BOOT_PROPERTY_NAME,
					initialSkipBoot);
		}
		collector.close();
	}

	@Parameters
	public static Collection<Object[]> parameters() {
		// Returns pairs: first member is skipBoot?, second member is
		// debug option
		final ArrayList<Object[]> result = new ArrayList<Object[]>();
		final Boolean[] FT = { Boolean.FALSE, Boolean.TRUE };
		final String[] DEBUG_OPTION = { "", Bootstrap.DEBUG_OPTION_ON,
				Bootstrap.DEBUG_OPTION_OFF };
		for (Boolean skipBoot : FT) {
			for (String debugOption : DEBUG_OPTION) {
				result.add(new Object[] { skipBoot, debugOption });
			}
		}
		return result;
	}

	@Test
	public void testHelp() throws IOException {
		ArrayList<String> args = new ArrayList<String>();
		if (!"".equals(debugOption)) {
			args.add(Bootstrap.DEBUG_OPTION);
			args.add(debugOption);
		}
		args.add("-help");
		String[] args_ = args.toArray(new String[args.size()]);
		Bootstrap.main(args_);
		collector.close();
		assertTrue(collector.has("^usage:"));
	}

}
