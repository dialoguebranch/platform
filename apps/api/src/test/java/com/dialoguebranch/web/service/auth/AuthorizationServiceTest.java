package com.dialoguebranch.web.service.auth;

import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link AuthorizationService}: resolving a user's roles to permissions and enforcing a
 * required {@link Permission}, including the 403 / {@code INSUFFICIENT_PRIVILEGES} failure shape.
 */
class AuthorizationServiceTest {

	private static AuthenticationInfo user(String... roles) {
		return new AuthenticationInfo("https://test/realms/test", "alice-sub", "alice", roles, new Date(), null);
	}

	@Test
	void adminMayCreateAProject() {
		assertDoesNotThrow(() -> AuthorizationService.require(user("admin"), Permission.PROJECT_CREATE));
	}

	@Test
	void participantMayRunDialoguesButNotCreateProjects() {
		assertDoesNotThrow(() -> AuthorizationService.require(user("participant"), Permission.DIALOGUE_RUN));
		assertThrows(ForbiddenException.class,
				() -> AuthorizationService.require(user("participant"), Permission.PROJECT_CREATE));
	}

	@Test
	void editorMayAuthorButNotDeleteAProject() {
		assertDoesNotThrow(() -> AuthorizationService.require(user("editor"), Permission.DIALOGUE_AUTHOR));
		assertThrows(ForbiddenException.class,
				() -> AuthorizationService.require(user("editor"), Permission.PROJECT_DELETE));
	}

	@Test
	void multipleRolesGrantTheUnionOfTheirPermissions() {
		assertDoesNotThrow(() -> AuthorizationService.require(
				user("participant", "editor"), Permission.DIALOGUE_AUTHOR));
	}

	@Test
	void aNullUserIsDenied() {
		assertThrows(ForbiddenException.class,
				() -> AuthorizationService.require(null, Permission.DIALOGUE_RUN));
	}

	@Test
	void aUserWithNoRecognisedRolesIsDenied() {
		assertThrows(ForbiddenException.class,
				() -> AuthorizationService.require(user("account", "offline_access"), Permission.DIALOGUE_RUN));
	}

	@Test
	void theForbiddenExceptionCarriesTheInsufficientPrivilegesCodeAndAHelpfulMessage() {
		ForbiddenException ex = assertThrows(ForbiddenException.class,
				() -> AuthorizationService.require(user("participant"), Permission.PROJECT_CREATE));
		assertEquals(ErrorCode.INSUFFICIENT_PRIVILEGES, ex.getError().getCode());
		assertTrue(ex.getMessage().contains("alice"));
		assertTrue(ex.getMessage().contains("PROJECT_CREATE"));
	}

	@Test
	void hasPermissionAgreesWithRequire() {
		assertTrue(AuthorizationService.hasPermission(user("participant"), Permission.DIALOGUE_RUN));
		assertFalse(AuthorizationService.hasPermission(user("participant"), Permission.PROJECT_CREATE));
		assertFalse(AuthorizationService.hasPermission(null, Permission.DIALOGUE_RUN));
	}
}
