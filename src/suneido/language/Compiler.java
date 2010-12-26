/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.language;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class Compiler {

	public static Object compile(String name, String src) {
		return compile(name, src, null);
	}

	public static Object compile(String name, String src, PrintWriter pw) {
		AstNode ast = parse(src);
		if (pw != null)
			pw.append(ast.toString() + "\n\n");
		return new AstCompile(name, pw).fold(ast);	}

	public static AstNode parse(String src) {
		Lexer lexer = new Lexer(src);
		AstGenerator generator = new AstGenerator();
		ParseConstant<AstNode, Generator<AstNode>> pc =
				new ParseConstant<AstNode, Generator<AstNode>>(lexer, generator);
		return pc.parse();
	}

	private static final Object[] noArgs = new Object[0];

	public static Object eval(String s) {
		Object f = compile("eval", "function () { " + s + " }");
		return Ops.call(f, noArgs);
	}

	public static void main(String[] args) throws IOException {
//		String s = Files.toString(new java.io.File("tmp.txt"), Charsets.UTF_8);
		String s = "function () { b1 = {|f| this; b2 = { f }; b2() }; b1(123) }";
		PrintWriter pw = new PrintWriter(System.out);
Object f =
		compile("Test", s, pw);
		System.out.println(" => " + Ops.call0(f));
		//System.out.println(" => " + Ops.call1(f, "hello"));
	}

}
