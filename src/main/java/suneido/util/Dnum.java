/* Copyright 2014 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.util;

import com.google.common.base.Strings;

/**
 * Decimal floating point number implementation
 * using a 64 bit long (treated as unsigned) for the coefficient.
 * <p>
 * Value is sign * coef * 10^exp, zeroed value is 0.
 * <p>
 * Math "sticks" at infinite when it overflows.
 * <p>
 * There is no NaN, inf / inf = 1, 0 / ... = 0, inf / ... = inf
 * <p>
 * Immutable as far as public methods, but mutable instances used internally.
 *
 * NOTE: Dnum IS NOT CURRENTLY USED
 */
public class Dnum implements Comparable<Dnum> { // MAYBE extend Number ???
	private long coef;
	private final byte sign;
	private byte exp;

	final static byte POS = +1;
	final static byte ZERO = 0;
	final static byte NEG = -1;
	public final static byte POS_INF = +2;
	public final static byte NEG_INF = -2;
	private static final int EXP_MIN = Byte.MIN_VALUE;
	private static final int EXP_MAX = Byte.MAX_VALUE;
	private final static long COEF_MAX = 9_999_999_999_999_999L;
	private final static int MAX_DIGITS = 16;
	private final static int MAX_SHIFT = MAX_DIGITS - 1;

	public final static Dnum Zero = new Dnum(ZERO, 0, 0);
	public final static Dnum One = new Dnum(POS, 1_000_000_000_000_000L, 1);
	public final static Dnum MinusOne = new Dnum(NEG, 1_000_000_000_000_000L, 1);
	public final static Dnum Inf = new Dnum(POS_INF, 1, 0);
	public final static Dnum MinusInf = new Dnum(NEG_INF, 1, 0);

	private final static long pow10[] = {
			1L,
			10L,
			100L,
			1000L,
			10000L,
			100000L,
			1000000L,
			10000000L,
			100000000L,
			1000000000L,
			10000000000L,
			100000000000L,
			1000000000000L,
			10000000000000L,
			100000000000000L,
			1000000000000000L,
			10000000000000000L,
			100000000000000000L,
			1000000000000000000L
			};

	private final static long halfpow10[] = { // for rounding
			0L,
			5L,
			50L,
			500L,
			5000L,
			50000L,
			500000L,
			5000000L,
			50000000L,
			500000000L,
			5000000000L,
			50000000000L,
			500000000000L,
			5000000000000L,
			50000000000000L,
			500000000000000L,
			5000000000000000L,
			50000000000000000L,
			500000000000000000L,
			5000000000000000000L
			};

	// raw - no normalization
	private Dnum(byte sign, long coef, int exp) {
		this.sign = sign;
		this.coef = coef;
		assert EXP_MIN <= exp && exp <= EXP_MAX;
		this.exp = (byte) exp;
	}

	public static Dnum from(long n) {
		if (n == 0)
			return Zero;
		byte sign = POS;
		if (n < 0) {
			n = -n;
			sign = NEG;
		}
		int p = maxShift(n);
		n *= pow10[p];
		return new Dnum(sign, n, MAX_DIGITS - p);
	}

	static int ilog10(long x)
		{
		// based on Hacker's Delight
		if (x == 0)
			return 0;
		int y = (19 * (63 - Long.numberOfLeadingZeros(x))) >> 6;
		if (y < 18 && x >= pow10[y + 1])
			++y;
		return y;
		}

	// the maximum we can safely shift left (*10)
	private static int maxShift(long x)
		{
		int i = ilog10(x);
		return i > MAX_SHIFT ? 0 : MAX_SHIFT - i;
		}

	public static Dnum from(int sign, long coef, int exp) {
		if (sign == 0 || coef == 0 || exp < EXP_MIN) {
			return Zero;
		} else if (sign == POS_INF) {
			return Inf;
		} else if (sign == NEG_INF) {
			return MinusInf;
		} else {
			boolean atmax = false;
			while (coef > COEF_MAX)
				{
				coef = (coef + 5) / 10; // drop/round least significant digit
				++exp;
				atmax = true;
				}
			if (! atmax) {
				int p = maxShift(coef);
				coef *= pow10[p];
				exp -= p;
			}
			if (exp > EXP_MAX)
				return inf(sign);
			return new Dnum(sign < 0 ? NEG : POS, coef, exp);
		}
	}

	private Dnum copy() {
		return new Dnum(sign, coef, exp);
	}

	private static class Parser {
		String s;
		int i = 0;
		int exp = 0;

		Parser(String s) {
			this.s = s;
		}

		Dnum parse() {
			byte sign = POS;
			if (match('-'))
				sign = NEG;
			else
				match('+');

			if (s.startsWith("inf", i))
				return inf(sign);

			long coef = getCoef();
			exp += getExp();
			if (i < s.length()) // didn't consume entire string
				throw new RuntimeException("invalid number");
			if (coef == 0 || exp < EXP_MIN)
				return Zero;
			else if (exp > EXP_MAX)
				return inf(sign);
			else
				return Dnum.from(sign, coef, exp);
		}

		private long getCoef() {
			boolean digits = false;
			boolean before_decimal = true;

			// skip leading zeroes, no effect on result
			while (match('0'))
				digits = true;

			long n = 0;
			int p = MAX_SHIFT;
			while (true)
				{
				if (isdigit(next()))
					{
					digits = true;
					if (next() != '0')
						{
						if (p < 0)
							throw new RuntimeException("too many digits");
						n += (next() - '0') * pow10[p];
						}
					--p;
					++i;
				} else if (before_decimal) {
					exp = MAX_SHIFT - p;
					if (! match('.'))
						break;
					before_decimal = false;
					if (!digits) {
						for (; match('0'); --exp)
							digits = true;
					}
				} else
					break;
				}
			if (!digits)
				throw new RuntimeException("numbers require at least one digit");
			return n;
		}

		int getExp() {
			int e = 0;
			if (match('e') || match('E'))
				{
				int esign = match('-') ? -1 : 1;
				match('+');
				for (; isdigit(next()); ++i)
					e = e * 10 + (next() - '0');
				e *= esign;
				}
			return e;
		}
		private char next() {
			return i < s.length() ? s.charAt(i) : 0;
		}
		private boolean match(char c) {
			if (next() == c) {
				i++;
				return true;
			}
			return false;
		}
		private static boolean isdigit(char c) {
			return '0' <= c && c <= '9';
		}
	}

	public static Dnum parse(String s) {
		return new Parser(s).parse();
	}

	@Override
	public String toString() {
		if (isZero())
			return "0";
		StringBuilder sb = new StringBuilder(20);
		if (sign < 0)
			sb.append('-');
		if (isInf())
			return sb.append("inf").toString();

		char digits[] = new char[MAX_SHIFT + 1];
		int i = MAX_SHIFT;
		int nd = 0;
		for (long c = coef; c != 0; --i, ++nd) {
			digits[nd] = (char) ('0' + (char) (c / pow10[i]));
			c %= pow10[i];
		}
		int e = exp - nd;
		if (0 <= e && e <= 4) {
			// decimal to the right
			sb.append(digits, 0, nd).append(Strings.repeat("0", e));
		} else if (-nd - 4 < e && e <= -nd) {
			// decimal to the left
			sb.append('.').append(Strings.repeat("0", -e - nd)).append(digits, 0, nd);
		} else if (-nd < e && e <= -1) {
			// decimal within
			int d = nd + e;
			sb.append(digits, 0, d);
			if (nd > 1)
				sb.append('.').append(digits, d, nd - d);
		} else {
			// use scientific notation
			sb.append(digits, 0, 1);
			if (nd > 1)
				sb.append('.').append(digits, 1, nd);
			sb.append('e').append(e + nd - 1);
		}
		return sb.toString();
	}

	/** for debug/test */
	String show() {
		StringBuilder sb = new StringBuilder();
		switch (sign) {
		case NEG_INF:	return "--";
		case NEG:		sb.append("-"); break;
		case 0:			sb.append("z"); break;
		case POS:		sb.append("+"); break;
		case POS_INF:	return "++";
		default:		sb.append("?"); break;
		}
		if (coef == 0)
			sb.append('0');
		else
			{
			sb.append(".");
			long c = coef;
			for (int i = MAX_SHIFT; i >= 0 && c != 0; --i)
				{
				long p = pow10[i];
				int digit = (int)(c / p);
				c %= p;
				sb.append((char) ('0' + digit));
				}
			}
		sb.append('e').append(exp);
		return sb.toString();
	}

	public boolean isZero() {
		return sign == ZERO;
	}

	/** @return true if plus or minus infinite */
	public boolean isInf() {
		return sign == NEG_INF || sign == POS_INF;
	}

	public Dnum neg() {
		return from(sign * -1, coef, exp);
	}

	public Dnum abs() {
		return sign < 0 ? from(-sign, coef, exp) : this;
	}

	public int sign() {
		return sign;
	}

	public int exp() {
		return exp;
	}

	public long coef() {
		return coef;
	}

	// add and subtract --------------------------------------------------------

	public static Dnum add(Dnum x, Dnum y) {
		if (x.isZero())
			return y;
		else if (y.isZero())
			return x;
		else if (x.is(Inf))
			if (y.is(MinusInf))
				return Zero;
			else
				return Inf;
		else if (x.is(MinusInf))
			if (y.is(Inf))
				return Zero;
			else
				return MinusInf;
		else if (y.is(Inf))
			return Inf;
		else if (y.is(MinusInf))
			return MinusInf;
		else if (x.sign != y.sign)
			return usub(x, y);
		else
			return uadd(x, y);
	}

	public static Dnum sub(Dnum x, Dnum y) {
		if (x.isZero())
			return y.neg();
		else if (y.isZero())
			return x;
		else if (x.is(Inf))
			if (y.is(Inf))
				return Zero;
			else
				return Inf;
		else if (x.is(MinusInf))
			if (y.is(MinusInf))
				return Zero;
			else
				return MinusInf;
		else if (y.is(Inf))
			return MinusInf;
		else if (y.is(MinusInf))
			return Inf;
		else if (x.sign != y.sign)
			return uadd(x, y);
		else
			return usub(x, y);
	}

	/** unsigned add */
	private static Dnum uadd(Dnum x, Dnum y) {
		if (x.exp > y.exp) {
			if (! align(x, y = y.copy()))
				return x;
		} else if (x.exp < y.exp)
			if (! align(y, x = x.copy()))
				return y;
		return from(x.sign, x.coef + y.coef, x.exp);
	}

	/** unsigned subtract */
	private static Dnum usub(Dnum x, Dnum y) {
		if (x.exp > y.exp) {
			if (! align(x, y = y.copy()))
				return x;
		} else if (x.exp < y.exp)
			if (! align(y, x = x.copy()))
				return y;
		return x.coef > y.coef
				? from(x.sign, x.coef - y.coef, x.exp)
				: from(-x.sign, y.coef - x.coef, x.exp);
	}

	/** WARNING: modifies y - requires defensive copy */
	private static boolean align(Dnum x, Dnum y) {
		int yshift = ilog10(y.coef);
		int e = x.exp - y.exp;
		if (e > yshift)
			return false;
		yshift = e;
		y.coef = (y.coef + halfpow10[yshift]) / pow10[yshift];
		y.exp += yshift;
		return true;
	}

	// multiply ----------------------------------------------------------------

	private static final long E8 = 100_000_000L;

	public static Dnum mul(Dnum x, Dnum y) {
		int sign = (x.sign * y.sign);
		if (sign == 0)
			return Zero;
		else if (x.isInf() || y.isInf())
			return inf(sign);
		int e = x.exp + y.exp;
		if (x.coef == 1)
			return from(sign, y.coef, e);
		if (y.coef == 1)
			return from(sign, x.coef, e);

		long xhi = x.coef / E8; // 8 digits
		long xlo = x.coef % E8; // 8 digits

		long yhi = y.coef / E8; // 8 digits
		long ylo = y.coef % E8; // 8 digits

		long c = xhi * yhi;
		if (xlo == 0)
			{
			if (ylo != 0)
				c += (xhi * ylo) / E8;
			}
		else if (ylo == 0)
			c += (xlo * yhi) / E8;
		else
			{
			long mid1 = xlo * yhi;
			long mid2 = ylo * xhi;
			c += (mid1 + mid2) / E8;
			}
		return from(sign, c, e);
	}

	// divide ------------------------------------------------------------------

	public static Dnum div(Dnum x, Dnum y) {
		if (x.isZero())
			return Zero;
		if (y.isZero())
			return inf(x.sign);
		int sign = x.sign * y.sign;
		if (x.isInf())
			return y.isInf()
					? sign < 0 ? MinusOne : One
					: inf(sign);
		if (y.isInf())
			return Zero;

		return div2(x, y);
	}

	private static Dnum div2(Dnum xn, Dnum yn) {
		int sign = xn.sign * yn.sign;
		yn = minCoef(yn);
		int exp = xn.exp - yn.exp + MAX_DIGITS;
		if (yn.coef == 1) // would be E15 before minCoef
			return from(sign, xn.coef, exp);
		long x = xn.coef;
		long y = yn.coef;
		long q = 0;
		while (true)
			{
			// ensure x > y so q2 > 0
			if (x < y)
				{
				if (!mul10safe(q))
					break;
				y /= 10; // drop least significant digit
				q *= 10;
				--exp;
				}

			long q2 = x / y;
			x %= y;
			q += q2;
			if (x == 0)
				break;

			// shift x (and q) to the left (max coef)
			// use full long extra range to reduce iterations
			int p = maxShiftLong(x > q ? x : q);
			if (p == 0)
				break;
			exp -= p;
			long pow = pow10[p];
			x *= pow;
			q *= pow;
			}
		return from(sign, q, exp);
	}

	private static final int E4 = 10_000;

	private static Dnum minCoef(Dnum dn) {
		// 16 decimal digits = at most 15 trailing decimal zeros
		long coef = dn.coef;
		int exp = dn.exp();
		int tz = Long.numberOfTrailingZeros(coef);
		if (tz >= 8 && (coef % E8) == 0)
			{
			coef /= E8;
			exp += 8;
			tz -= 8;
			}
		if (tz >= 4 && (coef % E4) == 0)
			{
			coef /= E4;
			exp += 4;
			tz -= 4;
			}
		while (tz > 0 && (coef % 10) == 0)
			{
			coef /= 10;
			exp += 1;
			tz -= 1;
			}
		return coef == dn.coef ? dn : new Dnum(dn.sign, coef, exp);
		}

	private static boolean mul10safe(long n) {
		return n <= Long.MAX_VALUE / 10;
	}

	private static int maxShiftLong(long x) {
		int i = ilog10(x);
		return i > 17 ? 0 : 17 - i;
	}

// -------------------------------------------------------------------------

	private static Dnum inf(int sign) {
		return sign < 0 ? MinusInf : sign > 0 ? Inf : Zero;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (coef ^ (coef >>> 32));
		result = prime * result + exp;
		result = prime * result + sign;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Dnum other = (Dnum) obj;
		return sign == other.sign && exp == other.exp && coef == other.coef;
	}

	// for tests, rounds off last digit
	static boolean almostSame(Dnum x, Dnum y)
		{
		return x.sign == y.sign && x.exp == y.exp &&
			((x.coef / 10) == (y.coef / 10) ||
			(x.coef + 5) / 10 == (y.coef + 5) / 10);
		}

	private boolean is(Dnum other) {
		return coef == other.coef && exp == other.exp && sign == other.sign;
	}

	@Override
	public int compareTo(Dnum that) {
		return cmp(this, that);
	}

	public static int cmp(Dnum x, Dnum y) {
		if (x.sign > y.sign)
			return +1;
		else if (x.sign < y.sign)
			return -1;
		int sign = x.sign;
		if (sign == 0 || sign == NEG_INF || sign == POS_INF)
			return 0;
		if (x.exp < y.exp)
			return -sign;
		if (x.exp > y.exp)
			return +sign;
		return sign * Long.compare(x.coef, y.coef);
	}

	public Dnum check() {
		assert NEG_INF <= sign && sign <= POS_INF :
			"Dnum invalid sign " + sign;
		assert sign != ZERO || coef == 0 :
			"Dnum sign is zero but coef is " + coef;
		assert sign == ZERO || coef != 0 :
			"Dnum coef is zero but sign is " + sign;
		return this;
	}

}
