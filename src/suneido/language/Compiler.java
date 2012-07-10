/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.language;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.concurrent.ThreadSafe;

/**
 * The components of the compiler are:
 * <ul>
 * <li>{@link Lexer} - scanner
 * <li>{@link ParseConstant}
 * <li>{@link ParseFunction}
 * <li>{@link ParseExpression}
 * <li>{@link AstGenerator} - generates an AST, based on calls from parsers
 * <li>{@link AstNode} - make up the AST
 * <li>{@link AstCompile} - compiles an AST to Java byte code
 * </ul>
 */
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

	public static Object eval(String s) {
		return Ops.call0(compile("eval", "function () { " + s + " }"));
	}

	public static void main(String[] args) throws IOException {
//		String s = Files.toString(new java.io.File("tmp.txt"), Charsets.UTF_8);
		String s = "function () { [a: 1, b: 2, c: 3, d: 4, e: 5, f: 6, g: 7, h: 8, i: 9, j: 10, k: 11] }";
		PrintWriter pw = new PrintWriter(System.out);
		//Object f =
		compile("Test", s, pw);
		//System.out.println(" => " + Ops.call0(f));
		//System.out.println(" => " + Ops.call1(f, "hello"));
	}

}
