package sub.fwb.parse;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.apache.solr.parser.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TokenFactoryTest {

	private TokenFactory factory;
	private String expanded = "";
	private String hlQuery = "";
	private Map<String, String> facetQueries;

	@Rule
	public ExpectedException inTest = ExpectedException.none();

	@Before
	public void beforeEach() throws Exception {
		factory = new TokenFactory();
	}

	@After
	public void afterEach() throws Exception {
		System.out.println(expanded);
		System.out.println(hlQuery);
		if (facetQueries != null) {
			System.out.println(facetQueries);
		}
	}

	@Test
	public void shouldCreateFacetQueriesForPrefixedRegex() throws Exception {
		facetQueries = facetQueriesFor("zitat:/regex/");
		assertEquals(1, facetQueries.size());
		String zitatFacet = facetQueries.get("zitat");
		assertEquals("/regex/", zitatFacet);
	}

	@Test
	public void shouldCreateFacetQueriesForRegex() throws Exception {
		facetQueries = facetQueriesFor("/regex/");
		String lemmaFacet = facetQueries.get("lemma");
		assertEquals("/regex/", lemmaFacet);
		String zitatFacet = facetQueries.get("zitat");
		assertEquals("/regex/", zitatFacet);
	}

	//@Test
	public void shouldCreateFacetQueriesForPrefixedComplexPhrase() throws Exception {
		facetQueries = facetQueriesFor("zitat:\"imbi? ward\"");
		assertEquals(1, facetQueries.size());
		String zitatFacet = facetQueries.get("zitat");
		assertEquals("_query_:\"{!complexphrase}zitat:\\\"imbi? ward\\\"\"", zitatFacet);
	}

	//@Test
	public void shouldCreateFacetQueriesForComplexPhrase() throws Exception {
		facetQueries = facetQueriesFor("\"imbi? ward\"");
		String lemmaFacet = facetQueries.get("lemma");
		assertEquals("_query_:\"{!complexphrase}lemma:\\\"imbi? ward\\\"\"", lemmaFacet);
		String zitatFacet = facetQueries.get("zitat");
		assertEquals("_query_:\"{!complexphrase}zitat:\\\"imbi? ward\\\"\"", zitatFacet);
	}

	@Test
	public void shouldCreateFacetQueriesForPrefixedPhrase() throws Exception {
		facetQueries = facetQueriesFor("zitat:\"imbis ward\"");
		assertEquals(1, facetQueries.size());
		String zitatFacet = facetQueries.get("zitat");
		assertEquals("\"imbis ward\"", zitatFacet);
	}

	@Test
	public void shouldCreateFacetQueriesForPhrase() throws Exception {
		facetQueries = facetQueriesFor("\"imbis ward\"");
		String lemmaFacet = facetQueries.get("lemma");
		assertEquals("\"imbis ward\"", lemmaFacet);
		String zitatFacet = facetQueries.get("zitat");
		assertEquals("\"imbis ward\"", zitatFacet);
	}

	@Test
	public void shouldCreateFacetQueriesPrefixedTerm() throws Exception {
		facetQueries = facetQueriesFor("lemma:imbis");
		assertEquals(1, facetQueries.size());
		String lemmaFacet = facetQueries.get("lemma");
		assertEquals("*imbis*", lemmaFacet);
	}

	@Test
	public void shouldCreateFacetQueriesForFuzzyTerm() throws Exception {
		facetQueries = facetQueriesFor("imbis~1");
		String lemmaFacet = facetQueries.get("lemma");
		assertEquals("imbis~1", lemmaFacet);
		String defFacet = facetQueries.get("def");
		assertEquals("imbis~1", defFacet);
	}

	@Test
	public void shouldCreateFacetQueriesForTerm() throws Exception {
		facetQueries = facetQueriesFor("imbis");
		String lemmaFacet = facetQueries.get("lemma");
		assertEquals("*imbis*", lemmaFacet);
		String defFacet = facetQueries.get("def");
		assertEquals("*imbis*", defFacet);
	}

	@Test
	public void shouldRemoveParenthesesFromPhrase() throws Exception {
		expanded = expandOneTokenString("\"104 a)\"");
		assertEquals("(\"104 a \" +(artikel:\"104 a \" zitat:\"104 a \")) ", expanded);
	}

	@Test
	public void shouldNotAcceptTooLongString() throws Exception {
	    inTest.expect(ParseException.class);
	    inTest.expectMessage("Suchanfrage zu lang");
		expanded = expandOneTokenString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
	}

	@Test
	public void shouldHighlightLemmaPhrase() throws Exception {
		hlQuery = hlQueryFrom("lemma:\"imbis bla\"");
		assertEquals("lemma_text:\"imbis bla\" ", hlQuery);
	}

	@Test
	public void shouldHighlightLemma() throws Exception {
		hlQuery = hlQueryFrom("lemma:imbis");
		assertEquals("lemma_text:*imbis* ", hlQuery);
	}

	@Test
	public void shouldIgnoreNonlettersInPrefixedPhrase() throws Exception {
		expanded = expandOneTokenString("lemma:\"‒&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘imb#is‒ bla&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘'\"");
		assertEquals("lemma:\"imbis bla\" ", expanded);
	}

	@Test
	public void shouldIgnoreNonlettersInPhrase() throws Exception {
		expanded = expandOneTokenString("\"‒&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘imb#is‒&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘'\"");
		assertEquals("(imbis +(artikel:imbis zitat:imbis)) ", expanded);
	}

	@Test
	public void shouldIgnoreNonlettersInPrefixed() throws Exception {
		expanded = expandOneTokenString("lemma:‒&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘imb#is‒&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘'");
		assertEquals("lemma:(imbis imbis* *imbis*)^1000 ", expanded);
	}

	@Test
	public void shouldIgnoreNonletters() throws Exception {
		expanded = expandOneTokenString("‒&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘imb#is‒&<>′`″”∣%«»‛⅓⅙⅔·⅕#˄˚{}¼¾©@‚°=½§…℔₰¶⸗˺˹„“+–!;›‹.,’·‘'");
		assertEquals("(imbis imbis* *imbis* +(artikel:*imbis* zitat:*imbis* sufo:*imbis*)) ", expanded);
	}

	@Test
	public void shouldHlRegexPrefixed() throws Exception {
		hlQuery = hlQueryFrom("zitat:/regex/");
		assertEquals("zitat_text:/regex/ ", hlQuery);
	}

	@Test
	public void shouldHlRegex() throws Exception {
		hlQuery = hlQueryFrom("/regex/");
		assertEquals("artikel_text:/regex/ zitat_text:/regex/ ", hlQuery);
	}

	@Test(expected=ParseException.class)
	public void shouldNotAllowComplexInQuote() throws Exception {
		hlQuery = hlQueryFrom("zitat:\"imbis wa?d\"");
	}

	@Test
	public void shouldHlComplexPhrasePrefixed() throws Exception {
		hlQuery = hlQueryFrom("def:\"imbis wa?d\"");
		assertEquals("_query_:\"{!complexphrase}def_text:\\\"imbis wa?d\\\"\" ", hlQuery);
	}

	@Test
	public void shouldHlComplexPhrase() throws Exception {
		hlQuery = hlQueryFrom("\"imbis wa?d\"");
		assertEquals("_query_:\"{!complexphrase}artikel_text:\\\"imbis wa?d\\\"\" _query_:\"{!complexphrase}zitat_text:\\\"imbis wa?d\\\"\" ", hlQuery);
	}

	@Test
	public void shouldHlPhrasePrefixed() throws Exception {
		hlQuery = hlQueryFrom("zitat:\"imbis ward\"");
		assertEquals("zitat_text:\"imbis ward\" ", hlQuery);
	}

	@Test
	public void shouldHlPhrase() throws Exception {
		hlQuery = hlQueryFrom("\"imbis ward\"");
		assertEquals("artikel_text:\"imbis ward\" zitat_text:\"imbis ward\" ", hlQuery);
	}

	@Test
	public void shouldHlPrefixedWithFuzzy() throws Exception {
		hlQuery = hlQueryFrom("zitat:imbis~1");
		assertEquals("zitat_text:imbis~1 ", hlQuery);
	}

	@Test
	public void shouldHlPrefixedWithDollar() throws Exception {
		hlQuery = hlQueryFrom("zitat:imbis$");
		assertEquals("zitat_text:*imbis ", hlQuery);
	}

	@Test
	public void shouldHlPrefixedWithCircumflex() throws Exception {
		hlQuery = hlQueryFrom("zitat:^imbis");
		assertEquals("zitat_text:imbis* ", hlQuery);
	}

	@Test
	public void shouldHlPrefixedWithCircumflexAndDollar() throws Exception {
		hlQuery = hlQueryFrom("zitat:^imbis$");
		assertEquals("zitat_text:imbis ", hlQuery);
	}

	@Test
	public void shouldHlPrefixedSearch() throws Exception {
		hlQuery = hlQueryFrom("zitat:imbis");
		assertEquals("zitat_text:*imbis* ", hlQuery);
	}

	@Test
	public void shouldHlInQuote() throws Exception {
		hlQuery = hlQueryFrom("zitat:imbis");
		assertEquals("zitat_text:*imbis* ", hlQuery);
	}

	@Test
	public void shouldHlWithFuzzy() throws Exception {
		hlQuery = hlQueryFrom("imbis~1");
		assertEquals("artikel_text:imbis~1 zitat_text:imbis~1 ", hlQuery);
	}

	@Test
	public void shouldHlWithCircumflex() throws Exception {
		hlQuery = hlQueryFrom("^imbis");
		assertEquals("artikel_text:imbis* zitat_text:imbis* ", hlQuery);
	}

	@Test
	public void shouldHlWithDollar() throws Exception {
		hlQuery = hlQueryFrom("imbis$");
		assertEquals("artikel_text:*imbis zitat_text:*imbis ", hlQuery);
	}

	@Test
	public void shouldHlWithCircumflexAndDollar() throws Exception {
		hlQuery = hlQueryFrom("^imbis$");
		assertEquals("artikel_text:imbis zitat_text:imbis ", hlQuery);
	}

	@Test
	public void shouldHlOneWord() throws Exception {
		hlQuery = hlQueryFrom("imbis");
		assertEquals("artikel_text:*imbis* zitat_text:*imbis* sufo_text:*imbis* ", hlQuery);
	}

	@Test
	public void shouldIgnoreDollarWhenTildePrefixed() throws Exception {
		expanded = expandOneTokenString("lemma:imbis$~2");
		assertEquals("lemma:imbis~2^1000 ", expanded);
	}

	@Test
	public void shouldAcceptTildeTwoPrefixed() throws Exception {
		expanded = expandOneTokenString("lemma:imbis~2");
		assertEquals("lemma:imbis~2^1000 ", expanded);
	}

	@Test
	public void shouldIgnoreCircumflexAndDollarWhenTilde() throws Exception {
		expanded = expandOneTokenString("^imbis$~2");
		assertEquals("(imbis~2 +(artikel:imbis~2 zitat:imbis~2)) ", expanded);
	}

	@Test
	public void shouldAcceptTildeTwo() throws Exception {
		expanded = expandOneTokenString("imbis~2");
		assertEquals("(imbis~2 +(artikel:imbis~2 zitat:imbis~2)) ", expanded);
	}

	@Test
	public void shouldAcceptTildeOne() throws Exception {
		expanded = expandOneTokenString("imbis~1");
		assertEquals("(imbis~1 +(artikel:imbis~1 zitat:imbis~1)) ", expanded);
	}

	@Test
	public void shouldAcceptDollarPrefixed() throws Exception {
		expanded = expandOneTokenString("lemma:imbis$");
		assertEquals("lemma:*imbis^1000 ", expanded);
	}

	@Test
	public void shouldAcceptCircumflexPrefixed() throws Exception {
		expanded = expandOneTokenString("lemma:^imbis");
		assertEquals("lemma:(imbis imbis*)^1000 ", expanded);
	}

	@Test
	public void shouldAcceptCircumflexAndDollarPrefixed() throws Exception {
		expanded = expandOneTokenString("lemma:^imbis$");
		assertEquals("lemma:imbis^1000 ", expanded);
	}

	@Test
	public void shouldAcceptDollar() throws Exception {
		expanded = expandOneTokenString("imbis$");
		assertEquals("(*imbis +(artikel:*imbis zitat:*imbis)) ", expanded);
	}

	@Test
	public void shouldAcceptCircumflex() throws Exception {
		expanded = expandOneTokenString("^imbis");
		assertEquals("(imbis imbis* +(artikel:imbis* zitat:imbis*)) ", expanded);
	}

	@Test
	public void shouldAcceptCircumflexAndDollar() throws Exception {
		expanded = expandOneTokenString("^imbis$");
		assertEquals("(imbis +(artikel:imbis zitat:imbis)) ", expanded);
	}

	@Test(expected = ParseException.class)
	public void shouldRejectIncomplete() throws Exception {
		expanded = expandOneTokenString("zitat:");
	}

	@Test(expected = ParseException.class)
	public void shouldRejectEndingWithColon() throws Exception {
		expanded = expandOneTokenString("zitat:bla:");
	}

	@Test(expected = ParseException.class)
	public void shouldRejectTwoColons() throws Exception {
		expanded = expandOneTokenString("lemma:imbis:bla");
	}

	@Test
	public void shouldAcceptOneWordInComplexPhraseWithPrefix() throws Exception {
		expanded = expandOneTokenString("zitat:\"imb?s\"");
		assertEquals("zitat:imb?s^50 ", expanded);
	}

	@Test
	public void shouldAcceptLeadingWildcardsInPhrase() throws Exception {
		expanded = expandOneTokenString("\"imbis ?ard\"");
		// used to throw an exception
	}

	@Test
	public void shouldNotRejectOneWordInComplexPhrase() throws Exception {
		expanded = expandOneTokenString("\"imb?s\"");
		assertEquals("(imb?s +(artikel:imb?s zitat:imb?s)) ", expanded);
	}

	@Test
	public void shouldExpandComplexPhraseWithPrefix() throws Exception {
		expanded = expandOneTokenString("zitat:\"imb*s ward\"");
		assertEquals("_query_:\"{!complexphrase}zitat:\\\"imb*s ward\\\"\" ", expanded);
	}

	@Test
	public void shouldExpandComplexPhrase() throws Exception {
		expanded = expandOneTokenString("\"imb*s ward\"");
		assertEquals(
				"(_query_:\"{!complexphrase}\\\"imb*s ward\\\"\" +(_query_:\"{!complexphrase}artikel:\\\"imb*s ward\\\"\" _query_:\"{!complexphrase}zitat:\\\"imb*s ward\\\"\")) ",
				expanded);
	}

	@Test
	public void shouldExpandRegexWithPrefix() throws Exception {
		expanded = expandOneTokenString("lemma:/imbis/");
		assertEquals("lemma:/imbis/ ", expanded);
	}

	@Test
	public void shouldExpandRegex() throws Exception {
		expanded = expandOneTokenString("/imbis/");
		assertEquals("(artikel:/imbis/ zitat:/imbis/) ", expanded);
	}

	@Test
	public void shouldExpandWithDash() throws Exception {
		expanded = expandOneTokenString("-lach");
		assertEquals("(\\-lach \\-lach* *\\-lach* +(artikel:*\\-lach* zitat:*\\-lach* sufo:*\\-lach*)) ", expanded);
	}

	@Test(expected = ParseException.class)
	public void shouldRejectUnknownFieldName() throws Exception {
		expanded = expandOneTokenString("lemma2:imbis");
	}

	@Test
	public void shouldExpandPrefixedSearch() throws Exception {
		expanded = expandOneTokenString("lemma:imbis");
		assertEquals("lemma:(imbis imbis* *imbis*)^1000 ", expanded);
	}

	@Test
	public void shouldExpandOneWordPhraseWithPrefix() throws Exception {
		expanded = expandOneTokenString("lemma:\"imbis\"");
		assertEquals("lemma:imbis^1000 ", expanded);
	}

	@Test
	public void shouldExpandOneWordPhrase() throws Exception {
		expanded = expandOneTokenString("\"imbis\"");
		assertEquals("(imbis +(artikel:imbis zitat:imbis)) ", expanded);
	}

	@Test
	public void shouldEscapeBrackets() throws Exception {
		expanded = expandOneTokenString("imb[i]s");
		assertEquals("(imb\\[i\\]s imb\\[i\\]s* *imb\\[i\\]s* +(artikel:*imb\\[i\\]s* zitat:*imb\\[i\\]s* sufo:*imb\\[i\\]s*)) ", expanded);
	}

	@Test
	public void shouldEscapeParentheses() throws Exception {
		expanded = expandOneTokenString("imb(i)s");
		assertEquals("(imb\\(i\\)s imb\\(i\\)s* *imb\\(i\\)s* +(artikel:*imb\\(i\\)s* zitat:*imb\\(i\\)s* sufo:*imb\\(i\\)s*)) ", expanded);
	}

	@Test
	public void shouldEscapePipe() throws Exception {
		expanded = expandOneTokenString("bar|tuch");
		assertEquals("(bar\\|tuch bar\\|tuch* *bar\\|tuch* +(artikel:*bar\\|tuch* zitat:*bar\\|tuch* sufo:*bar\\|tuch*)) ", expanded);
	}

	@Test(expected = ParseException.class)
	public void shouldRejectIncompletePhrase() throws Exception {
		expanded = expandOneTokenString("my imbis\"");
	}

	@Test(expected = ParseException.class)
	public void shouldRejectUnfinishedPhrase() throws Exception {
		expanded = expandOneTokenString("\"my imbis");
	}

	@Test
	public void shouldExpandSimplePhrase() throws Exception {
		expanded = expandOneTokenString("\"my imbis\"");
		assertEquals("(\"my imbis\" +(artikel:\"my imbis\" zitat:\"my imbis\")) ", expanded);
	}

	@Test
	public void shouldExpandOneWord() throws Exception {
		expanded = expandOneTokenString("imbis");
		assertEquals("(imbis imbis* *imbis* +(artikel:*imbis* zitat:*imbis* sufo:*imbis*)) ", expanded);
	}

	private String expandOneTokenString(String ts) throws Exception {
		return factory.createTokens(ts, "lemma^1000 def^70 zitat^50", false).get(0).getModifiedQuery();
	}

	private String hlQueryFrom(String query) throws Exception {
		return factory.createTokens(query, "lemma^1000 def^70 zitat^50", false).get(0).getHlQuery();
	}

	private Map<String, String> facetQueriesFor(String query) throws Exception {
		return factory.createTokens(query, "lemma^1000 def^70 zitat^50", false).get(0).getFacetQueries();
	}
}
