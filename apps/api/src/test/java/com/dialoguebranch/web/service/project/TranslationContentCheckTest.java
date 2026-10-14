package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.project.DraftDialogueService.CheckedTranslationContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link DraftDialogueService#checkTranslationContent}, which checks translation file
 * content and re-serialises it before it's stored (#376).
 */
class TranslationContentCheckTest {

	private static CheckedTranslationContent check(String content) {
		return DraftDialogueService.checkTranslationContent(content);
	}

	private static void assertRefused(String content, String expectedProblem) {
		CheckedTranslationContent checked = check(content);
		assertNull(checked.content());
		assertTrue(checked.problems().stream().anyMatch(p -> p.contains(expectedProblem)),
				"expected a problem containing '" + expectedProblem + "', got " +
						checked.problems());
	}

	@Test
	void validContentIsReSerialisedKeepingSpeakersContextsAndOrder() {
		CheckedTranslationContent checked = check("{\"Martin McOwl\":{\"Hello.\":\"Hallo.\"," +
				"\"Bye $name.\":\"Dag $name.\"},\"_user\":{\"female_addressee\":" +
				"{\"Thanks.\":\"Bedankt.\"}}}");
		assertEquals(java.util.List.of(), checked.problems());
		assertEquals(3, checked.translationCount());
		assertEquals("""
				{
				  "Martin McOwl" : {
				    "Hello." : "Hallo.",
				    "Bye $name." : "Dag $name."
				  },
				  "_user" : {
				    "female_addressee" : {
				      "Thanks." : "Bedankt."
				    }
				  }
				}""", checked.content());
	}

	@Test
	void emptyTranslationsAndTheGroupsTheyLeaveEmptyAreDropped() {
		CheckedTranslationContent checked = check("{\"Martin\":{\"Hello.\":\"Hallo.\"," +
				"\"Bye.\":\"  \"},\"Anna\":{\"Hi.\":\"\"}}");
		assertEquals(1, checked.translationCount());
		assertEquals("{\n  \"Martin\" : {\n    \"Hello.\" : \"Hallo.\"\n  }\n}", checked.content());
	}

	@Test
	void anEmptyObjectIsValidWithoutTranslations() {
		CheckedTranslationContent checked = check("{}");
		assertTrue(checked.problems().isEmpty());
		assertEquals(0, checked.translationCount());
		assertEquals("{ }", checked.content());
	}

	@Test
	void missingOrBlankContentIsRefused() {
		assertRefused(null, "the translation is empty");
		assertRefused("  \n", "the translation is empty");
	}

	@Test
	void invalidJsonIsRefusedWithItsLocation() {
		assertRefused("{\"Martin\": {\"Hello.\": \"Hallo.\"", "not valid JSON (line 1");
	}

	@Test
	void jsonThatIsntAnObjectIsRefused() {
		assertRefused("[\"Hello.\"]", "not a JSON object");
		assertRefused("\"Hello.\"", "not a JSON object");
	}

	@Test
	void anEntryWithoutASpeakerIsRefused() {
		assertRefused("{\"Hello.\":\"Hallo.\",\"Martin\":{\"Bye.\":\"Dag.\"}}",
				"\"Hello.\" has no speaker");
	}

	@Test
	void aValueThatIsNeitherATranslationNorAGroupIsRefused() {
		assertRefused("{\"Martin\":{\"Hello.\":null}}", "\"Hello.\" must have a translation text");
		assertRefused("{\"Martin\":{\"Hello.\":[\"Hallo.\"]}}", "\"Hello.\" must have a translation text");
		assertRefused("{\"Martin\":42}", "\"Martin\" must have a translation text");
	}

	@Test
	void translationParserErrorsAreRefused() {
		assertRefused("{\"Martin\":{\"Hello [[x|y]].\":\"Hallo.\"}}", "Failed to parse translation key");
		assertRefused("{\"Martin\":{\"Hello.\":\"Hallo [[a|b]].\"}}", "Failed to parse translation value");
		assertRefused("{\"Martin\":{\"Hello.\":\"Hallo.\"},\"Martin \":{\"Hello.\":\"Dag.\"}}",
				"duplicate translation");
	}

	@Test
	void contentTheParserCrashesOnIsRefused() {
		// TranslationParser currently crashes on a command other than <<input>> in a translation
		// instead of reporting it; the content must be refused either way.
		assertRefused("{\"Martin\":{\"Hello.\":\"Hallo <<if $x>>.\"}}", "could not be read");
	}
}
