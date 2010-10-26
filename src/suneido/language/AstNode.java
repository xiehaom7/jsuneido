package suneido.language;

import java.util.*;

import com.google.common.base.Strings;

public class AstNode {
	public final Token token;
	public final String value;
	public final List<AstNode> children;
	public final Object extra;

	public final static List<AstNode> emptyList = Collections.emptyList();
	public final static AstNode nullNode = new AstNode(null, null, emptyList, null);

	public AstNode(Token token, String value, List<AstNode> children, Object extra) {
		this.token = token;
		this.value = value;
		this.children = children;
		this.extra = extra;
	}

	public AstNode(Token token, String value) {
		this(token, value, emptyList, null);
	}

	public AstNode(Token token, AstNode... children) {
		this(token, null, Arrays.asList(children), null);
	}

	public AstNode(Token token, String value, AstNode... children) {
		this(token, value, Arrays.asList(children), null);
	}

	public AstNode(Token token, List<AstNode> children) {
		this(token, null, children, null);
	}

	public static AstNode of(Token token, Object extra, AstNode... children) {
		return new AstNode(token, null, Arrays.asList(children), extra);
	}

	public AstNode first() {
		return children.get(0);
	}
	public AstNode second() {
		return children.get(1);
	}
	public AstNode third() {
		return children.get(2);
	}
	public AstNode fourth() {
		return children.get(3);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	private String toString(int indent) {
		boolean multiline = multiline();
		String sep = multiline ? "\n" : " ";
		int childIndent = multiline ? indent + 3 : 0;

		StringBuilder sb = new StringBuilder();
		sb.append(Strings.repeat(" ", indent));
		sb.append('(').append(token);
		if (value != null)
			sb.append("=").append(value);
		if (extra != null)
			sb.append(sep).append("*").append(extra);
		if (children != null)
			for (AstNode x : children)
				sb.append(sep).append(x == null ? "null" : x.toString(childIndent));
		sb.append(')');
		return sb.toString();
	}

	boolean multiline() {
		final int MAX = 70;
		int total = 0;
		for (AstNode x : children) {
			String s = (x == null) ? "null" : x.toString();
			if (s.contains("\n"))
				return true;
			int n = s.length();
			total += n;
			if (n > MAX || total > MAX)
				return true;
		}
		return false;
	}

	public static void main(String[] args) {
		System.out.println(new AstNode(Token.ADD,
				new AstNode(Token.IDENTIFIER, "x"),
				new AstNode(Token.NUMBER, "123")));
	}

}
