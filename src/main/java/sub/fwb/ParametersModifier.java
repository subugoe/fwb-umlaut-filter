package sub.fwb;

import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import org.apache.solr.parser.ParseException;

import sub.fwb.parse.ParseUtil;
import sub.fwb.parse.TokenFactory;
import sub.fwb.parse.tokens.OperatorNot;
import sub.fwb.parse.tokens.OperatorOr;
import sub.fwb.parse.tokens.ParenthesisLeft;
import sub.fwb.parse.tokens.ParenthesisRight;
import sub.fwb.parse.tokens.QueryToken;
import sub.fwb.ParametersModifyingSearchHandler.ModifiedParameters; 

public class ParametersModifier {

	private String expandedQuery = "";
	private String hlQuery = "";
	private String queryFieldsWithBoosts = "";
	private String hlFields = "";

	public ParametersModifier(String qf, String hlFl) {
		queryFieldsWithBoosts = qf;
		hlFields = hlFl;
	}

	public ModifiedParameters changeParamsForQuery(String origQuery) throws ParseException {
		
		boolean exactSearch = false;
		if (origQuery.contains("EXAKT")) {
			origQuery = origQuery.replace("EXAKT", "");
			exactSearch = true;
		}

		TokenFactory factory = new TokenFactory(queryFieldsWithBoosts);
		List<QueryToken> allTokens = factory.createTokens(origQuery, exactSearch);

		if (mustAddParens(allTokens)) {
			addParenthesesWhereNecessary(allTokens, factory);
		} else {
			checkIfParensCorrect(allTokens);
			setNOTsInParens(allTokens, factory);
		}

		for (QueryToken token : allTokens) {
			expandedQuery += token.getModifiedQuery();
			hlQuery += token.getHlQuery();
		}

		modifyHlFields(exactSearch);

		return new ModifiedParameters(expandedQuery.trim(), hlQuery.trim(), "", hlFields);
	}

	private boolean mustAddParens(List<QueryToken> allTokens) {
		boolean hasORorNOT = false;
		for (QueryToken token : allTokens) {
			if (token instanceof ParenthesisLeft || token instanceof ParenthesisRight) {
				return false;
			}
			if (token instanceof OperatorOr || token instanceof OperatorNot) {
				hasORorNOT = true;
			}
		}
		return hasORorNOT;
	}

	private void addParenthesesWhereNecessary(List<QueryToken> allTokens, TokenFactory factory) throws ParseException {
		allTokens.add(0, factory.createOneToken("(", false));
		allTokens.add(allTokens.size(), factory.createOneToken(")", false));
		for (int i = allTokens.size() - 2; i > 0; i--) {
			QueryToken current = allTokens.get(i);
			if (current instanceof OperatorOr) {
				allTokens.add(i + 1, factory.createOneToken("(", false));
				allTokens.add(i, factory.createOneToken(")", false));
			} else if (current instanceof OperatorNot) {
				allTokens.add(i + 2, factory.createOneToken(")", false));
				allTokens.add(i + 1, factory.createOneToken("(", false));
			}
		}
	}

	private void checkIfParensCorrect(List<QueryToken> allTokens) throws ParseException {
		String wrongParensMessage = "Klammern sind nicht richtig gesetzt";
		Stack<QueryToken> stack = new Stack<>();
		try {
			for (QueryToken token : allTokens) {
				if (token instanceof ParenthesisLeft) {
					stack.push(token);
				} else if (token instanceof ParenthesisRight) {
					stack.pop();
				}
			}
			if (!stack.isEmpty()) {
				throw new ParseException(wrongParensMessage);
			}
		} catch (EmptyStackException e) {
			throw new ParseException(wrongParensMessage);
		}
	}

	private void setNOTsInParens(List<QueryToken> allTokens, TokenFactory factory) throws ParseException {
		for (int i = allTokens.size() - 2; i >= 0; i--) {
			QueryToken current = allTokens.get(i);
			QueryToken next = allTokens.get(i + 1);
			if (current instanceof OperatorNot && !(next instanceof ParenthesisLeft)) {
				allTokens.add(i + 2, factory.createOneToken(")", false));
				allTokens.add(i + 1, factory.createOneToken("(", false));
			}
		}
	}

	private void modifyHlFields(boolean exactSearch) {
		if (exactSearch) {
			String[] exploded = hlFields.split(",");
			hlFields = "";
			for (String field : exploded) {
				hlFields += field + ParseUtil.EXACT + ",";
			}
			hlFields = hlFields.substring(0, hlFields.length() - 1);
		}
	}
}