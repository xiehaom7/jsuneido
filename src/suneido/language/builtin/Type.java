package suneido.language.builtin;

import suneido.language.*;

public class Type extends BuiltinFunction {

	@Override
	public Object call(Object... args) {
		args = Args.massage(FunctionSpec.value, args);
		return Ops.typeName(args[0]);
	}

}
