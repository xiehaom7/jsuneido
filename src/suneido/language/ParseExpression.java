package suneido.language;

import static suneido.language.Token.*;
import suneido.database.query.ParseQuery;

public class ParseExpression<T, G extends Generator<T>> extends Parse<T, G> {
	boolean EQ_as_IS = false;
	private boolean inQuery = false;

	public ParseExpression(Lexer lexer, G generator) {
		super(lexer, generator);
	}

	public ParseExpression(Parse<T, G> parse) {
		super(parse);
		if (parse instanceof ParseQuery)
			inQuery = true;
	}

	public void eq_as_is() {
		EQ_as_IS = true;
	}

	public T parse() {
		return matchReturn(EOF, expression());
	}

	public T expression() {
		return conditionalExpression();
	}

	private T conditionalExpression() {
		T first = orExpression();
		if (token == Q_MARK) {
			Object label = generator.ifExpr(first);
			++statementNest;
			match(Q_MARK);
			T t = expression();
			label = generator.conditionalTrue(label, t);
			match(COLON);
			--statementNest;
			T f = expression();
			return generator.conditional(first, t, f, label);
		} else {
			return first;
		}
	}

	private T orExpression() {
		T result = andExpression();
		if (token == OR) {
			Object label = generator.orStart();
			result = generator.or(label, null, result);
			while (token == OR) {
				matchSkipNewlines();
				result = generator.or(label, result, andExpression());
			}
			result = generator.orEnd(label, result);
		}
		return result;
	}

	private T andExpression() {
		T result = inExpression();
		if (token == AND) {
			Object label = generator.andStart();
			result = generator.and(label, null, result);
			do {
				matchSkipNewlines();
				result = generator.and(label, result, inExpression());
			} while (token == AND);
			result = generator.andEnd(label, result);
		}
		return result;
	}

	private T inExpression() {
		T expr = bitorExpression();
		if (matchIf(IN)) {
			expr = generator.in(expr, null);
			match(L_PAREN);
			while (token != R_PAREN) {
				expr = generator.in(expr, constant());
				matchIf(COMMA);
			}
			match(R_PAREN);
		}
		return expr;
	}

	private T bitorExpression() {
		T result = bitxorExpression();
		while (token == BITOR) {
			matchSkipNewlines();
			result = generator.binaryExpression(BITOR, result, bitxorExpression());
		}
		return result;
	}

	private T bitxorExpression() {
		T result = bitandExpression();
		while (token == BITXOR) {
			matchSkipNewlines();
			result = generator.binaryExpression(BITXOR, result, bitandExpression());
		}
		return result;
	}

	private T bitandExpression() {
		T result = isExpression();
		while (token == BITAND) {
			matchSkipNewlines();
			result = generator.binaryExpression(BITAND, result, isExpression());
		}
		return result;
	}

	private T isExpression() {
		T result = compareExpression();
		while (token == IS || token == ISNT ||
				token == MATCH || token == MATCHNOT ||
				(EQ_as_IS && token == EQ)) {
			Token op = (token == EQ ? IS : token);
			matchSkipNewlines();
			result = generator.binaryExpression(op, result, compareExpression());
		}
		return result;
	}

	private T compareExpression() {
		T result = shiftExpression();
		while (token == LT || token == LTE || token == GT || token == GTE) {
			Token op = token;
			matchSkipNewlines();
			result = generator.binaryExpression(op, result, shiftExpression());
		}
		return result;
	}

	private T shiftExpression() {
		T result = addExpression();
		while (token == LSHIFT || token == RSHIFT) {
			Token op = token;
			matchSkipNewlines();
			result = generator.binaryExpression(op, result, addExpression());
		}
		return result;
	}

	private T addExpression() {
		T result = mulExpression();
		while (token == ADD || token == SUB || token == CAT) {
			Token op = token;
			matchSkipNewlines();
			result = generator.binaryExpression(op, result, mulExpression());
		}
		return result;
	}

	private T mulExpression() {
		T result = unaryExpression();
		while (token == MUL || token == DIV || token == MOD) {
			Token op = token;
			matchSkipNewlines();
			result = generator.binaryExpression(op, result, unaryExpression());
		}
		return result;
	}

	private T unaryExpression() {
		switch (token) {
		case ADD:
		case SUB:
		case NOT:
		case BITNOT:
			Token op = token;
			match();
			return generator.unaryExpression(op, unaryExpression());
		default:
			return lexer.getKeyword() == NEW ? newExpression() : term();
		}
	}
	private T newExpression() {
		match(NEW);
		T term = term(true);
		generator.newCall();
		T args = token == L_PAREN ? arguments() : null;
		return generator.newExpression(term, args);
	}

	private T term() {
		return term(false);
	}

	private T term(boolean newTerm) {
		Token preincdec = null;
		if (token == INC || token == DEC) {
			preincdec = token;
			match();
		}
		T term = primary();
		while (token == DOT || token == L_BRACKET ||
				token == L_PAREN || token == L_CURLY) {
			if (newTerm && token == L_PAREN)
				return term;
			if (token == DOT) {
				matchSkipNewlines(DOT);
				String id = lexer.getValue();
				match(IDENTIFIER);
				term = generator.member(term, id);
				if (!expectingCompound && token == NEWLINE && lookAhead() == L_CURLY)
					match();
			} else if (matchIf(L_BRACKET)) {
				term = generator.subscript(term, expression());
				match(R_BRACKET);
			} else if (token == L_PAREN || token == L_CURLY) {
				term = generator.functionCallTarget(term);
				term = generator.functionCall(term, arguments());
			}
		}
		if (preincdec != null)
			term = generator.preIncDec(term, preincdec);
		else if (assign()) {
			Token op = token;
			matchSkipNewlines();
			term = generator.lvalueForAssign(term, op);
			T expr = expression();
			term = generator.assignment(term, op, expr);
		} else if (token == INC || token == DEC) {
			term = generator.postIncDec(term, token);
			match();
		}
		return term;
	}

	private T primary() {
		switch (token) {
		case NUMBER:
		case STRING:
		case HASH:
			return generator.constant(constant());
		case L_CURLY:
			return block();
		case DOT:
			// note: DOT not matched
			return generator.selfRef();
		case L_PAREN:
			match(L_PAREN);
			return matchReturn(R_PAREN, generator.rvalue(expression()));
		case L_BRACKET:
			if (inQuery)
				return generator.constant(constant());
			else {
				match(L_BRACKET);
				// TODO optimize literal part like cSuneido
				T func = generator.identifier("Record");
				return generator.functionCall(func, argumentList(R_BRACKET));
			}
		case IDENTIFIER:
			return primaryIdentifier();
		default:
			syntaxError();
			return null; // unreachable
		}
	}

	private T primaryIdentifier() {
		switch (lexer.getKeyword()) {
		case FUNCTION:
		case CLASS:
		case DLL:
		case STRUCT:
		case CALLBACK:
			return generator.constant(constant());
		case TRUE:
		case FALSE:
			return matchReturn(generator.constant(generator.bool(lexer.getKeyword() == TRUE)));
		case SUPER:
			match(SUPER);
			return superCall();
		default:
			String identifier = lexer.getValue();
			if (isGlobal(identifier) && lookAhead(! expectingCompound) == L_CURLY)
				return generator.constant(constant());
			else
				return matchReturn(IDENTIFIER, generator.identifier(identifier));
		}
	}

	private T superCall() {
		if (token == L_PAREN)
			return generator.superCallTarget("New");
		if (token != DOT)
			syntaxError("invalid use of super");
		match();
		if (token != IDENTIFIER)
			syntaxError("invalid use of super");
		String method = lexer.getValue();
		match();
		if (token != L_PAREN)
			syntaxError("invalid use of super");
		return generator.superCallTarget(method);
	}


	private boolean assign() {
		return (EQ_as_IS && token == EQ) ? false : token.assign();
	}
	private boolean isGlobal(String value) {
		char c = value.charAt(0);
		if (c == '_')
			c = value.charAt(1);
		return Character.isUpperCase(c);
	}
	private T arguments() {
		T args = null;
		if (matchIf(L_PAREN)) {
			if (matchIf(AT))
				return atArgument();
			else
				args = argumentList(R_PAREN);
		}
		if (token == NEWLINE && !expectingCompound && lookAhead() == L_CURLY)
			match();
		if (token == L_CURLY) {
			generator.argumentName("block");
			args = generator.argumentList(args, generator.string("block"), block());
		}
		return args;
	}
	private T atArgument() {
		String n = "0";
		if (matchIf(ADD)) {
			n = lexer.getValue();
			match(NUMBER);
		}
		generator.atArgument(n);
		T expr = expression();
		match(R_PAREN);
		return generator.atArgument(n, expr);
	}

	private T argumentList(Token closing) {
		T args = null;
		Object keyword = null;
		while (token != closing) {
			if (lookAhead() == COLON) {
				keyword = keyword();
			} else if (keyword != null)
				syntaxError("un-named arguments must come before named arguments");

			Token ahead = lookAhead();
			boolean trueDefault = (keyword != null &&
					(token == COMMA || token == closing || ahead == COLON));

			if (keyword != null && isConstantArg(closing)) {
				args = generator.argumentListConstant(args, keyword,
						trueDefault ? generator.bool(true) : constant());
			} else {
				if (keyword != null)
					generator.argumentName(keyword);
				T value = trueDefault
						? generator.constant(generator.bool(true))
						: expression();
				args = generator.argumentList(args, keyword, value);
			}
			matchIf(COMMA);
		}
		match(closing);
		return args;
	}
	private Object keyword() {
		Object keyword = null;
		if (token == STRING || token == IDENTIFIER)
			keyword = generator.string(lexer.getValue());
		else if (token == NUMBER)
			keyword = generator.number(lexer.getValue());
		else
			syntaxError("invalid keyword");
		match();
		match(COLON);
		return keyword;
	}
	// MAYBE factor out duplication with ParseConstant.isMemberName
	private boolean isConstantArg(Token closing) {
// doesn't work for #20100101.Format()
//		if (token == HASH)
//			return true;
		if (!isConstant())
			return false;
		Lexer ahead = new Lexer(lexer);
		Token t = ahead.nextSkipNewlines();
		if (t == COMMA || t == closing)
			return true;
		if (t != IDENTIFIER && t != STRING
				&& t != NUMBER && t != SUB && t != ADD)
			return false;
		if (t == SUB || t == ADD)
			t = ahead.next();
		t = ahead.next();
		return t == COLON;
	}

	private boolean isConstant() {
		switch (token) {
		case NUMBER:
		case STRING:
			return true;
		case IDENTIFIER:
			switch (lexer.getKeyword()) {
			case TRUE:
			case FALSE:
				return true;
			}
		}
		return false;
	}

	private T block() {
		Object loop = generator.blockBegin();
		match(L_CURLY);
		T params = token == BITOR ? blockParams() : null;
		generator.blockParams();
		T statements = statementList(loop);
		match(R_CURLY);
		return generator.blockEnd(params, statements);
	}
	private T blockParams() {
		match(BITOR);
		T params = null;
		if (matchIf(AT)) {
			params = generator.parameters(params, "@" + lexer.getValue(), null);
			match(IDENTIFIER);
		} else
			while (token == IDENTIFIER) {
				params = generator.parameters(params, lexer.getValue(), null);
				match(IDENTIFIER);
				matchIf(COMMA);
			}
		match(BITOR);
		return params;
	}

	private T constant() {
		ParseConstant<T, G> p = new ParseConstant<T, G>(this);
		T result = p.constant();
		token = p.token;
		return result;
	}

	private T statementList(Object loop) {
		ParseFunction<T, G> p = new ParseFunction<T, G>(this);
		T result = p.statementList(loop);
		token = p.token;
		return result;
	}
}
