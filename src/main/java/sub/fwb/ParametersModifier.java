package sub.fwb;

import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.solr.parser.ParseException;

import sub.fwb.ParametersModifyingSearchHandler.ModifiedParameters;
import sub.fwb.parse.ParseUtil;
import sub.fwb.parse.TokenFactory;
import sub.fwb.parse.tokens.ComplexPhrase;
import sub.fwb.parse.tokens.ComplexPhrasePrefixed;
import sub.fwb.parse.tokens.OperatorAnd;
import sub.fwb.parse.tokens.OperatorNot;
import sub.fwb.parse.tokens.OperatorOr;
import sub.fwb.parse.tokens.ParenthesisLeft;
import sub.fwb.parse.tokens.ParenthesisRight;
import sub.fwb.parse.tokens.QueryToken;
import sub.fwb.parse.tokens.QueryTokenSearchString;

public class ParametersModifier {

	private String queryFieldsWithBoosts = "";
	private String hlFields = "";
	private Map<String, String> allFacetQueries = new HashMap<>();

	public ParametersModifier(String qf, String hlFl) {
		queryFieldsWithBoosts = qf;
		hlFields = hlFl;
	}

	public ModifiedParameters changeParamsForQuery(final String origQuery) throws ParseException {
		String expandedQuery = "";
		String hlQuery = "";
		String defType = "";

		String modifiedQuery = origQuery;
		boolean exactSearch = false;
		if (origQuery.contains("EXAKT")) {
			modifiedQuery = modifiedQuery.replace("EXAKT", "");
			exactSearch = true;
		}

		modifiedQuery = addSpaces(modifiedQuery);

		modifyQueryFields(exactSearch);
		TokenFactory factory = new TokenFactory();
		List<QueryToken> allTokens = factory.createTokens(modifiedQuery, queryFieldsWithBoosts, exactSearch);

		checkIfParensCorrect(allTokens);
		checkIfOperatorsCorrect(allTokens);
		addANDs(allTokens, factory);
		setNOTsInParens(allTokens, factory);
		if (thereAreORs(allTokens)) {
			setANDsInParens(allTokens, factory);
		}

		for (QueryToken token : allTokens) {
			expandedQuery += token.getModifiedQuery();
			hlQuery += token.getHlQuery();
			addToFacetQueries(token.getFacetQueries());
		}

		expandedQuery = addFieldTypeIfNecessary(expandedQuery);

		if (expandedQuery.isEmpty()) {
			throw new ParseException("Die Suchanfrage ist ungültig");
		}

		if (hlFields != null && !hlFields.isEmpty()) {
			modifyHlFields(exactSearch);
		}

		Set<String> facetQueries = facetQueryMapToSet(allTokens.size());
		if (hasComplexPhrase(allTokens)) {
			// the complex phrase parser only seems to work when this is set
			defType = "lucene";
		}
		return new ModifiedParameters(expandedQuery.trim(), hlQuery.trim(), queryFieldsWithBoosts, hlFields, defType, facetQueries);
	}

	private boolean hasComplexPhrase(List<QueryToken> allTokens) {
		for (QueryToken qt : allTokens) {
			if (qt instanceof ComplexPhrase || qt instanceof ComplexPhrasePrefixed)
				return true;
		}
		return false;
	}

	private String addSpaces(String query) {
		query = query.replace(")(", ") (");
		query = query.replace("NOT(", "NOT (");
		query = query.replace("AND(", "AND (");
		query = query.replace("OR(", "OR (");
		query = query.replace(")NOT", ") NOT");
		query = query.replace(")AND", ") AND");
		query = query.replace(")OR", ") OR");
		return query;
	}

	private boolean thereAreORs(List<QueryToken> allTokens) {
		for (QueryToken token : allTokens) {
			if (token instanceof OperatorOr) {
				return true;
			}
		}
		return false;
	}

	private void addANDs(List<QueryToken> allTokens, TokenFactory factory) throws ParseException {
		for (int i = allTokens.size() - 2; i >= 0; i--) {
			QueryToken current = allTokens.get(i);
			QueryToken following = allTokens.get(i + 1);
			boolean termAndTerm = current instanceof QueryTokenSearchString
					&& following instanceof QueryTokenSearchString;
			boolean parenRightAndTerm = current instanceof ParenthesisRight
					&& following instanceof QueryTokenSearchString;
			boolean termAndParenLeft = current instanceof QueryTokenSearchString
					&& following instanceof ParenthesisLeft;
			boolean parenRightAndParenLeft = current instanceof ParenthesisRight
					&& following instanceof ParenthesisLeft;
			boolean termAndNOT = current instanceof QueryTokenSearchString && following instanceof OperatorNot;
			boolean parenRightAndNOT = current instanceof ParenthesisRight && following instanceof OperatorNot;
			if (termAndTerm || parenRightAndTerm || termAndParenLeft || parenRightAndParenLeft || termAndNOT
					|| parenRightAndNOT) {
				allTokens.add(i + 1, factory.createOneToken("AND", queryFieldsWithBoosts, false));
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

	private void checkIfOperatorsCorrect(List<QueryToken> allTokens) throws ParseException {
		String incorrectOperatorsMessage = "Operatoren sind nicht richtig gesetzt";
		for (int i = 0; i < allTokens.size(); i++) {
			QueryToken current = allTokens.get(i);
			boolean isFirst = (i == 0);
			boolean isLast = (i == allTokens.size() - 1);
			boolean isANDorOR = current instanceof OperatorAnd || current instanceof OperatorOr;
			boolean startsWithANDorOR = isFirst && isANDorOR;
			boolean isANDorORorNOT = isANDorOR || current instanceof OperatorNot;
			boolean endsWithOperator = isLast && isANDorORorNOT;
			if (startsWithANDorOR || endsWithOperator) {
				throw new ParseException(incorrectOperatorsMessage);
			}
			if (!isFirst && !isLast) {
				QueryToken next = allTokens.get(i + 1);
				QueryToken previous = allTokens.get(i - 1);
				boolean expectedPrevious = previous instanceof QueryTokenSearchString
						|| previous instanceof ParenthesisRight;
				boolean isWrongPrevious = isANDorOR && !expectedPrevious;
				boolean expectedNext = next instanceof QueryTokenSearchString || next instanceof ParenthesisLeft
						|| next instanceof OperatorNot;
				boolean isWrongNext = isANDorORorNOT && !expectedNext;
				if (isWrongPrevious || isWrongNext) {
					throw new ParseException(incorrectOperatorsMessage);
				}
			}
		}
	}

	private void setNOTsInParens(List<QueryToken> allTokens, TokenFactory factory) throws ParseException {
		for (int i = allTokens.size() - 2; i >= 0; i--) {
			QueryToken current = allTokens.get(i);
			if (!(current instanceof OperatorNot)) {
				continue;
			}
			int depthToTheRight = 0;
			for (int j = i + 1; j < allTokens.size(); j++) {
				boolean isLastToken = (j == allTokens.size() - 1);
				if (isLastToken) {
					allTokens.add(factory.createOneToken(")", queryFieldsWithBoosts, false));
					break;
				}
				QueryToken currentToTheRight = allTokens.get(j);
				boolean isCorrectRightParen = currentToTheRight instanceof ParenthesisRight && depthToTheRight == 0;
				boolean isCorrectOR = currentToTheRight instanceof OperatorOr && depthToTheRight == 0;
				boolean isCorrectAND = currentToTheRight instanceof OperatorAnd && depthToTheRight == 0;
				if (isCorrectRightParen || isCorrectOR || isCorrectAND) {
					allTokens.add(j, factory.createOneToken(")", queryFieldsWithBoosts, false));
					break;
				}
				if (currentToTheRight instanceof ParenthesisLeft) {
					depthToTheRight++;
				} else if (currentToTheRight instanceof ParenthesisRight) {
					depthToTheRight--;
				}
			}
			allTokens.add(i, factory.createOneToken("(", queryFieldsWithBoosts, false));
		}
	}

	private void setANDsInParens(List<QueryToken> allTokens, TokenFactory factory) throws ParseException {
		for (int i = allTokens.size() - 2; i >= 0; i--) {
			QueryToken current = allTokens.get(i);
			if (!(current instanceof OperatorAnd)) {
				continue;
			}
			int depthToTheRight = 0;
			for (int j = i; j < allTokens.size(); j++) {
				boolean isLastToken = (j == allTokens.size() - 1);
				if (isLastToken) {
					allTokens.add(factory.createOneToken(")", queryFieldsWithBoosts, false));
					break;
				}
				QueryToken currentToTheRight = allTokens.get(j);
				boolean isCorrectRightParen = currentToTheRight instanceof ParenthesisRight && depthToTheRight == 0;
				boolean isCorrectOR = currentToTheRight instanceof OperatorOr && depthToTheRight == 0;
				if (isCorrectRightParen || isCorrectOR) {
					allTokens.add(j, factory.createOneToken(")", queryFieldsWithBoosts, false));
					break;
				}
				if (currentToTheRight instanceof ParenthesisLeft) {
					depthToTheRight++;
				} else if (currentToTheRight instanceof ParenthesisRight) {
					depthToTheRight--;
				}
			}
			int depthToTheLeft = 0;
			for (int j = i; j >= 0; j--) {
				boolean isFirstToken = j == 0;
				if (isFirstToken) {
					allTokens.add(0, factory.createOneToken("(", queryFieldsWithBoosts, false));
					break;
				}
				QueryToken currentToTheLeft = allTokens.get(j);
				boolean isCorrectLeftParen = currentToTheLeft instanceof ParenthesisLeft && depthToTheLeft == 0;
				boolean isCorrectOR = currentToTheLeft instanceof OperatorOr && depthToTheLeft == 0;
				if (isCorrectLeftParen || isCorrectOR) {
					allTokens.add(j + 1, factory.createOneToken("(", queryFieldsWithBoosts, false));
					break;
				}
				if (currentToTheLeft instanceof ParenthesisRight) {
					depthToTheLeft++;
				} else if (currentToTheLeft instanceof ParenthesisLeft) {
					depthToTheLeft--;
				}
			}
		}
	}

	private void addToFacetQueries(Map<String, String> tokenFacets) {
		for (Map.Entry<String, String> entry : tokenFacets.entrySet()) {
			String lemmaEtc = entry.getKey();
			String currentValue = entry.getValue();
			if (allFacetQueries.containsKey(lemmaEtc)) {
				String previousValue = allFacetQueries.get(lemmaEtc);
				allFacetQueries.put(lemmaEtc, previousValue + " " + currentValue);
			} else {
				allFacetQueries.put(lemmaEtc, currentValue);
			}
		}
	}

	private String addFieldTypeIfNecessary(String query) {
		if (query.contains("NOT")) {
			return query.trim() + " -type:quelle";
		}
		return query;
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

	private void modifyQueryFields(boolean exactSearch) {
		if (exactSearch) {
			String[] fields = queryFieldsWithBoosts.trim().split("\\s+");
			queryFieldsWithBoosts = "";
			for (String fieldWithBoost : fields) {
				String fieldName = fieldWithBoost.split("\\^")[0];
				String boostValue = "^" + fieldWithBoost.split("\\^")[1];
				queryFieldsWithBoosts += fieldName + ParseUtil.EXACT + boostValue + " ";
			}
			queryFieldsWithBoosts = queryFieldsWithBoosts.trim();
		}
	}

	private Set<String> facetQueryMapToSet(int numberOfTokens) {
		Set<String> setOfFacetQueries = new HashSet<>();
		for (Map.Entry<String, String> entry : allFacetQueries.entrySet()) {
			String lemmaEtc = entry.getKey();
			if (lemmaEtc.startsWith("sufo")) {
				// the sufo and sufo_exakt fields must be hidden from the user
				continue;
			}
			String currentValue = entry.getValue();
			if (numberOfTokens == 1) {
				setOfFacetQueries.add(lemmaEtc + ":" + currentValue);
			} else {
				setOfFacetQueries.add(lemmaEtc + ":(" + currentValue + ")");
			}
		}
		return setOfFacetQueries;
	}
}
