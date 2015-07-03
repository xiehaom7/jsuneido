/* Copyright 2015 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime.builtin;

import java.lang.ref.SoftReference;

import suneido.SuContainer;
import suneido.compiler.AstNode;
import suneido.runtime.Ops;
import suneido.runtime.Params;
import suneido.runtime.VirtualContainer;

public class AstParse {

	@Params("string")
	public static Object AstParse(Object a) {
		String src = Ops.toStr(a);
		AstNode ast = suneido.compiler.Compiler.parse(src);
		return new AstWrapper(ast);
	}

	private static class AstWrapper extends VirtualContainer {
		AstNode node;
		SoftReference<SuContainer> ob = new SoftReference<>(null);

		public AstWrapper(AstNode node) {
			this.node = node;
		}

		@Override
		public String typeName() {
			return "AstNode";
		}

		@Override
		protected SuContainer value() {
			SuContainer c = ob.get();
			if (c == null) {
				ob = new SoftReference<>(c = new SuContainer());
				c.put("Token", node.token.toString());
				if (node.value != null)
					c.put("Value", node.value);
				c.put("Line", node.lineNumber);
				if (node.children == null)
					c.put("Children", SuContainer.EMPTY);
				else {
					SuContainer c2 = new SuContainer(node.children.size());
					for (AstNode child : node.children)
						if (child != null)
							c2.add(new AstWrapper(child));
					c.put("Children", c2);
				}
			}
			return c;
		}

		@Override
		public String toString() {
			return ob.get() == null ? "AstNode" : super.toString();
		}

	}
}