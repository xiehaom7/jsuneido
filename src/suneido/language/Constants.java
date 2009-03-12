package suneido.language;

import java.util.*;

import suneido.SuValue;

/**
 * Stores an array of constants for each generated class.
 * {@link CompileGenerator} calls put.
 *
 * @author Andrew McKinlay
 */
public class Constants {
	private static Map<String, SuValue[]> constants =
			new HashMap<String, SuValue[]>();

	private static final SuValue[] eg = new SuValue[0];

	public static void put(String name, List<SuValue> x) {
		constants.put(name, x.toArray(eg));
	}

	public static SuValue[] get(String name) {
		return constants.get(name);
	}
}
