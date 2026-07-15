package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.web.service.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A project declares an exact, fixed set of language codes it supports — for the seeded {@code
 * default-test} project, that's {@code "en"} (source) plus translations {@code "nl-NL"} and
 * {@code "pt-PT"}. Language resolution must match a requested language exactly against that set;
 * it must not do locale-aware/fuzzy matching (e.g. treating a bare {@code "nl"} as equivalent to
 * the declared {@code "nl-NL"}) — a language outside the declared set falls back to the source
 * language instead.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceLanguageMatchingTest {

    @Autowired
    private Application application;

    @Test
    void exactlyDeclaredLanguageResolvesToItsOwnTranslation() throws Exception {
        ResourcePointer resolved =
                resolveMenuDialogue("nl-NL");

        assertNotNull(resolved, "the 'menu' dialogue should be found in the 'default-test' project");
        assertEquals("nl-NL", resolved.getLanguage(),
                "requesting the exact declared code nl-NL should resolve to its own translation");
    }

    @Test
    void undeclaredLanguageFallsBackToSourceLanguage() throws Exception {
        ResourcePointer resolved = resolveMenuDialogue("nl");

        assertNotNull(resolved, "the 'menu' dialogue should be found in the 'default-test' project");
        assertEquals("en", resolved.getLanguage(),
                "requesting a language the project doesn't declare (nl, vs. the declared nl-NL) " +
                        "should fall back to the source language, not fuzzy-match nl-NL");
    }

    private ResourcePointer resolveMenuDialogue(String language) throws Exception {
        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "language-matching-test-user-" + UUID.randomUUID();
        UserService userService = applicationManager.getOrCreateActiveUserService(userId);
        return userService.getDialogueDescriptionFromProject("default-test", "menu", language);
    }

}
