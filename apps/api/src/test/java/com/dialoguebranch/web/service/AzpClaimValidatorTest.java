package com.dialoguebranch.web.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link AzpClaimValidator}: the {@code azp} (client) allow-list check applied to every
 * Keycloak token on top of signature/issuer/expiry validation.
 */
class AzpClaimValidatorTest {

	private Logger validatorLogger;
	private ListAppender<ILoggingEvent> logAppender;

	private Level originalLevel;

	@BeforeEach
	void attachLogAppender() {
		validatorLogger = (Logger) LoggerFactory.getLogger(AzpClaimValidator.class);
		originalLevel = validatorLogger.getLevel();
		validatorLogger.setLevel(Level.DEBUG); // so the DEBUG-after-cap path is observable too
		logAppender = new ListAppender<>();
		logAppender.start();
		validatorLogger.addAppender(logAppender);
	}

	@AfterEach
	void detachLogAppender() {
		validatorLogger.detachAppender(logAppender);
		validatorLogger.setLevel(originalLevel);
	}

	private static Jwt tokenWithAzp(String azp) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
				.header("alg", "RS256")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.subject("user");
		if (azp != null) {
			builder.claim("azp", azp);
		}
		return builder.build();
	}

	private long warnCount() {
		return logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
	}

	private long logCount() {
		return logAppender.list.size();
	}

	@Test
	void acceptsATokenWhoseAzpIsInTheAllowList() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("dlb-web-service", "sibling-service"));
		assertFalse(validator.validate(tokenWithAzp("sibling-service")).hasErrors());
	}

	@Test
	void rejectsATokenWhoseAzpIsNotInTheAllowList() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("dlb-web-service"));
		OAuth2TokenValidatorResult result = validator.validate(tokenWithAzp("some-other-client"));
		assertTrue(result.hasErrors());
		assertEquals("invalid_token", result.getErrors().iterator().next().getErrorCode());
	}

	@Test
	void rejectsATokenWithNoAzpClaim() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("dlb-web-service"));
		assertTrue(validator.validate(tokenWithAzp(null)).hasErrors());
	}

	@Test
	void wildcardEntryAcceptsAnyAzp() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("*"));
		assertFalse(validator.validate(tokenWithAzp("anything-at-all")).hasErrors());
	}

	@Test
	void wildcardEntryAcceptsATokenWithNoAzpClaim() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("*"));
		assertFalse(validator.validate(tokenWithAzp(null)).hasErrors());
	}

	@Test
	void rejectsAnAllowListThatMixesTheWildcardWithRealClientIds() {
		assertThrows(IllegalArgumentException.class,
				() -> new AzpClaimValidator(List.of("*", "dlb-web-service")));
	}

	@Test
	void logsAWarningOnlyOncePerDistinctRejectedAzp() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("dlb-web-service"));

		validator.validate(tokenWithAzp("intruder"));
		validator.validate(tokenWithAzp("intruder"));
		validator.validate(tokenWithAzp("intruder"));
		assertEquals(1, warnCount());

		validator.validate(tokenWithAzp("another-intruder"));
		assertEquals(2, warnCount());
	}

	@Test
	void stopsWarningOnceTheDeDuplicationCapIsReached() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("dlb-web-service"));
		for (int i = 0; i < AzpClaimValidator.WARN_CACHE_CAP + 20; i++) {
			validator.validate(tokenWithAzp("intruder-" + i));
		}
		assertEquals(AzpClaimValidator.WARN_CACHE_CAP, warnCount());
	}

	@Test
	void doesNotReLogARepeatOfAnAzpFirstSeenAfterTheCap() {
		AzpClaimValidator validator = new AzpClaimValidator(List.of("dlb-web-service"));
		for (int i = 0; i < AzpClaimValidator.WARN_CACHE_CAP; i++) {
			validator.validate(tokenWithAzp("intruder-" + i));
		}
		assertEquals(AzpClaimValidator.WARN_CACHE_CAP, logCount()); // all WARN, cap reached

		// A value first seen past the cap logs once (at DEBUG), then stays silent on repeats —
		// it must not log afresh on every request.
		validator.validate(tokenWithAzp("late-intruder"));
		long afterFirstSighting = logCount();
		assertEquals(AzpClaimValidator.WARN_CACHE_CAP + 1, afterFirstSighting);

		validator.validate(tokenWithAzp("late-intruder"));
		validator.validate(tokenWithAzp("late-intruder"));
		assertEquals(afterFirstSighting, logCount());
	}
}
