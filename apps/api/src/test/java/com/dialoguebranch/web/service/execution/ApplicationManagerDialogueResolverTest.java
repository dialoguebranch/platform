package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.execution.DialogueResolver;
import com.dialoguebranch.execution.ResolvedDialogue;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.web.service.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ApplicationManager#getDialogueResolver}, which finds the dialogues of a published
 * project for real playback (#473).
 *
 * <p>A project declares an exact, fixed set of language codes it supports — for the seeded {@code
 * default-test} project, that's {@code "en"} (source) plus translations {@code "nl-NL"} and
 * {@code "pt-PT"}. Language resolution must match a requested language exactly against that set;
 * it must not do locale-aware/fuzzy matching (e.g. treating a bare {@code "nl"} as equivalent to
 * the declared {@code "nl-NL"}) — a language outside the declared set falls back to the source
 * language instead.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationManagerDialogueResolverTest {

	@Autowired
	private Application application;

	private DialogueResolver resolver(String projectSlug) {
		return application.getApplicationManager().getDialogueResolver(projectSlug);
	}

	@Test
	void exactlyDeclaredLanguageResolvesToItsOwnTranslation() throws Exception {
		ResolvedDialogue resolved = resolver("default-test").resolve("menu", "nl-NL");

		assertEquals("nl-NL", resolved.pointer().getLanguage(),
				"requesting the exact declared code nl-NL should resolve to its own translation");
		assertEquals("menu", resolved.pointer().getDialogueName());
	}

	@Test
	void undeclaredLanguageFallsBackToSourceLanguage() throws Exception {
		ResolvedDialogue resolved = resolver("default-test").resolve("menu", "nl");

		assertEquals("en", resolved.pointer().getLanguage(),
				"requesting a language the project doesn't declare (nl, vs. the declared nl-NL) " +
						"should fall back to the source language, not fuzzy-match nl-NL");
	}

	@Test
	void withoutALanguageTheSourceLanguageIsUsed() throws Exception {
		assertEquals("en",
				resolver("default-test").resolve("menu", null).pointer().getLanguage());
	}

	@Test
	void theDialogueIsThePublishedOneWithItsGenderTranslations() throws Exception {
		ExecutableProject published = (ExecutableProject) application.getApplicationManager()
				.getProjects().get("default-test");
		ResolvedDialogue resolved = resolver("default-test").resolve("menu", "nl-NL");

		assertSame(published.getDialogues().get(resolved.pointer()), resolved.dialogue());
		assertNotNull(resolved.userGenderTranslations());
		assertSame(published.getDialogueForUserGender(resolved.pointer(),
						TranslationContext.Gender.FEMALE),
				resolved.userGenderTranslations().apply(TranslationContext.Gender.FEMALE));
	}

	@Test
	void anUnknownDialogueIsNotFoundInTheProject() {
		ExecutionException e = assertThrows(ExecutionException.class,
				() -> resolver("default-test").resolve("nonexistent", "en"));

		assertEquals(ExecutionException.Type.DIALOGUE_NOT_FOUND, e.getType());
		assertEquals("Dialogue 'nonexistent' not found in project 'default-test'.",
				e.getMessage());
	}

	@Test
	void aProjectThatIsntLoadedHasNoDialogues() {
		ExecutionException e = assertThrows(ExecutionException.class,
				() -> resolver("no-such-project").resolve("menu", "en"));

		assertEquals(ExecutionException.Type.DIALOGUE_NOT_FOUND, e.getType());
		assertEquals("Dialogue 'menu' not found in project 'no-such-project'.", e.getMessage());
	}
}
