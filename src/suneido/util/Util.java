package suneido.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;

import javax.annotation.concurrent.ThreadSafe;

import suneido.language.Ops;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * Miscellaneous functions.
 *
 * @author Andrew McKinlay
 * <p><small>Copyright 2008 Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.</small></p>
 */
@ThreadSafe
public class Util {

	@SuppressWarnings("unchecked")
	public static <T> String listToCommas(List<T> list) {
		if (list == null || list.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder();
		for (T x : list) {
			sb.append(",");
			if (x instanceof List)
				sb.append(listToParens((List<String>) x));
			else
				sb.append(Ops.toStr(x));
		}
		return sb.substring(1);
	}

	@SuppressWarnings("unchecked")
	public static <T> String displayListToCommas(List<T> list) {
		if (list == null || list.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder();
		for (T x : list) {
			sb.append(",");
			if (x instanceof List)
				sb.append(displayListToParens((List<String>) x));
			else
				sb.append(Ops.display(x));
		}
		return sb.substring(1);
	}

	public static <T> String listToParens(List<T> list) {
		return "(" + listToCommas(list) + ")";
	}

	public static <T> String displayListToParens(List<T> list) {
		return "(" + displayListToCommas(list) + ")";
	}

	public static final Splitter commaSplitter = Splitter.on(',');

	public static List<String> commasToList(String s) {
		if (s.isEmpty())
			return Collections.emptyList();
		return Lists.newArrayList(commaSplitter.split(s));
	}

	private static final Charset charset = Charset.forName("ISO-8859-1");

	public static String bufferToString(ByteBuffer buf) {
		int pos = buf.position();
		String s = charset.decode(buf).toString();
		buf.position(pos);
		return s;
	}

	public static ByteBuffer stringToBuffer(String s) {
		return ByteBuffer.wrap(s.getBytes(charset));
	}

	public static String bufferToHex(ByteBuffer buf) {
		String s = "";
		for (int i = buf.position(); i < buf.limit(); ++i)
			s += " " + String.format("%02x", buf.get(i));
		return s.substring(1);
	}

	public static int bufferUcompare(ByteBuf b1, ByteBuf b2) {
		int n = Math.min(b1.size(), b2.size());
		for (int i = 0; i < n; ++i) {
			int cmp = (b1.get(i) & 0xff) - (b2.get(i) & 0xff);
			if (cmp != 0)
				return cmp;
		}
		return b1.size() - b2.size();
	}

	public static int bufferUcompare(ByteBuffer b1, ByteBuffer b2) {
		int n = Math.min(b1.remaining(), b2.remaining());
		int b1pos = b1.position();
		int b2pos = b2.position();
		for (int i = 0; i < n; ++i) {
			int cmp = (b1.get(b1pos + i) & 0xff) - (b2.get(b2pos + i) & 0xff);
			if (cmp != 0)
				return cmp;
		}
		return b1.remaining() - b2.remaining();
	}

	public static ByteBuffer copyByteBuffer(ByteBuffer buf, int size) {
		byte[] data = new byte[size];
		// duplicate buffer if we need to set position
		// because modification is not thread safe
		if (buf.position() != 0) {
			buf = buf.duplicate();
			buf.position(0);
		}
		buf.get(data);
		return ByteBuffer.wrap(data);
	}

	public static String bytesToString(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length);
		for (byte b : bytes)
			sb.append((char) (b & 0xff));
		return sb.toString();
	}

	/**
	 * @return A new list containing all the values from x and y. x is copied as
	 *         is, so if it has duplicates they are retained. Duplicates from y
	 *         are not retained.
	 */
	public static <T> List<T> union(List<T> x, List<T> y) {
		return addUnique(new ArrayList<T>(x), y);
	}

	/** modifies list */
	public static <T> List<T> addUnique(List<T> list, List<T> x) {
		for (T s : x)
			if (!list.contains(s))
				list.add(s);
		return list;
	}

	/** modifies list */
	public static <T> List<T> addUnique(List<T> list, T x) {
		if (!list.contains(x))
			list.add(x);
		return list;
	}

	/** returns a new list */
	public static <T> List<T> withoutDups(List<T> x) {
		List<T> result = new ArrayList<T>();
		for (T s : x)
			if (!result.contains(s))
				result.add(s);
		return result;
	}

	/** returns a new list */
	public static <T> List<T> difference(List<T> x, List<T> y) {
		List<T> result = new ArrayList<T>();
		for (T s : x)
			if (!y.contains(s))
				result.add(s);
		return result;
	}

	/** returns a new list */
	public static <T> List<T> intersect(List<T> x, List<T> y) {
		List<T> result = new ArrayList<T>();
		for (T s : x)
			if (y.contains(s))
				result.add(s);
		return result;
	}

	public static <T> boolean startsWith(List<T> x, List<T> y) {
		if (y.size() > x.size())
			return false;
		for (int i = 0; i < y.size(); ++i)
			if (!x.get(i).equals(y.get(i)))
				return false;
		return true;
	}

	public static <T> boolean startsWithSet(List<T> list, List<T> set) {
		int set_size = set.size();
		if (list.size() < set_size)
			return false;
		for (int i = 0; i < set_size; ++i)
			if (!set.contains(list.get(i)))
				return false;
		return true;
	}

	public static <T> boolean setEquals(List<T> x, List<T> y) {
		int n = 0;
		for (T s : x)
			if (y.contains(s))
				++n;
		return n == x.size() && n == y.size();
	}

	public static <T> boolean nil(List<T> x) {
		return x == null || x.isEmpty();
	}

	/** returns a new list */
	public static <T> List<T> concat(List<T> x, List<T> y) {
		List<T> result = new ArrayList<T>(x);
		result.addAll(y);
		return result;
	}

	/** returns a new list */
	public static <T> List<T> without(List<T> list, T x) {
		List<T> result = new ArrayList<T>();
		for (T y : list)
			if (x == null ? y != null : !x.equals(y))
				result.add(y);
		return result;
	}

	public static <T> T[] array(T... values) {
		return values;
	}

	/**
	 * Based on C++ STL code.
	 *
	 * @param slot
	 * @return The <u>first</u> position where slot could be inserted without
	 *         changing the ordering.
	 */
	public static <T extends Comparable<? super T>> int lowerBound(
			List<T> list, T value) {
		int first = 0;
		int len = list.size();
		while (len > 0) {
			int half = len >> 1;
			int middle = first + half;
			if (list.get(middle).compareTo(value) < 0) {
				first = middle + 1;
				len -= half + 1;
			} else
				len = half;
		}
		return first;
	}
	public static <T> int lowerBound(
			List<T> list, T value, Comparator<? super T> comp) {
		int first = 0;
		int len = list.size();
		while (len > 0) {
			int half = len >> 1;
			int middle = first + half;
			if (comp.compare(list.get(middle), value) < 0) {
				first = middle + 1;
				len -= half + 1;
			} else
				len = half;
		}
		return first;
	}

	/**
	 * Based on C++ STL code.
	 *
	 * @return The <u>last</u> position where slot could be inserted without
	 *         changing the ordering.
	 */
	public static <T extends Comparable<? super T>> int upperBound(
			List<T> list, T value) {
		int first = 0;
		int len = list.size();
		while (len > 0) {
			int half = len >> 1;
			int middle = first + half;
			if (value.compareTo(list.get(middle)) < 0)
				len = half;
			else {
				first = middle + 1;
				len -= half + 1;
			}
		}
		return first;
	}
	public static <T> int upperBound(
			List<T> list, T value, Comparator<? super T> comp) {
		int first = 0;
		int len = list.size();
		while (len > 0) {
			int half = len >> 1;
			int middle = first + half;
			if (comp.compare(value, list.get(middle)) < 0)
				len = half;
			else {
				first = middle + 1;
				len -= half + 1;
			}
		}
		return first;
	}

	/**
	 * Based on C++ STL code.
	 *
	 * Equivalent to Range(lowerBound, upperBound)
	 *
	 * @return The largest subrange in which value could be inserted at any
	 *         place in it without changing the ordering.
	 */
	public static <T extends Comparable<? super T>> Range equalRange(
			List<T> list, T value) {
		int first = 0;
		int len = list.size();
		while (len > 0) {
			int half = len >> 1;
			int middle = first + half;
			T midvalue = list.get(middle);
			if (midvalue.compareTo(value) < 0) {
				first = middle + 1;
				len -= half + 1;
			} else if (value.compareTo(midvalue) < 0)
				len = half;
			else {
				int left = first + lowerBound(list.subList(first, middle), value);
				++middle;
				int right = middle + upperBound(list.subList(middle, first + len), value);
				return new Range(left, right);
			}
		}
		return new Range(first, first);
	}
	public static <T> Range equalRange(
			List<T> list, T value, Comparator<? super T> comp) {
		int first = 0;
		int len = list.size();
		while (len > 0) {
			int half = len >> 1;
			int middle = first + half;
			T midvalue = list.get(middle);
			if (comp.compare(midvalue, value) < 0) {
				first = middle;
				++first;
				len -= half + 1;
			} else if (comp.compare(value, midvalue) < 0)
				len = half;
			else {
				int left = first + lowerBound(list.subList(first, middle), value, comp);
				++middle;
				int right = middle + upperBound(list.subList(middle, first + len), value, comp);
				return new Range(left, right);
			}
		}
		return new Range(first, first);
	}

	@ThreadSafe
	public static final class Range {
		public final int left;
		public final int right;

		public Range(int left, int right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Range))
				return false;
			Range r = (Range) other;
			return left == r.left && right == r.right;
		}

		@Override
		public int hashCode() {
			int result = 17;
			result = 31 * result + left;
			result = 31 * result + right;
			return result;
		}

		@Override
		public String toString() {
			return "Range(" + left + "," + right + ")";
		}
	}

}
