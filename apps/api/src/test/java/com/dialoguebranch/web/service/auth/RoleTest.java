package com.dialoguebranch.web.service.auth;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link Role}: the fixed mapping from a Keycloak role id to a {@link Permission} set, and
 * the union helper {@link Role#permissionsForRoles(String[])} that {@link AuthorizationService}
 * builds on.
 */
class RoleTest {

	@Test
	void participantHasExactlyItsFourPermissions() {
		assertEquals(
				EnumSet.of(Permission.DIALOGUE_RUN, Permission.VARIABLE_READ_OWN,
						Permission.VARIABLE_READ_OWN_HISTORY, Permission.VARIABLE_WRITE_OWN,
						Permission.LOG_READ_OWN),
				Role.PARTICIPANT.getPermissions());
	}

	@Test
	void editorIsAStrictSupersetOfParticipant() {
		assertTrue(Role.EDITOR.getPermissions().containsAll(Role.PARTICIPANT.getPermissions()));
		assertTrue(Role.EDITOR.getPermissions().size() > Role.PARTICIPANT.getPermissions().size());
	}

	@Test
	void editorAddsAuthoringButNotProjectLifecycle() {
		assertTrue(Role.EDITOR.allows(Permission.DIALOGUE_AUTHOR));
		assertTrue(Role.EDITOR.allows(Permission.DIALOGUE_TEST));
		assertTrue(Role.EDITOR.allows(Permission.PUBLISH_READ));
		assertFalse(Role.EDITOR.allows(Permission.PROJECT_CREATE));
		assertFalse(Role.EDITOR.allows(Permission.PUBLISH_CREATE));
		assertFalse(Role.EDITOR.allows(Permission.USER_DELEGATE));
	}

	@Test
	void adminIsAStrictSupersetOfEditor() {
		assertTrue(Role.ADMIN.getPermissions().containsAll(Role.EDITOR.getPermissions()));
		assertTrue(Role.ADMIN.getPermissions().size() > Role.EDITOR.getPermissions().size());
	}

	@Test
	void adminGrantsEveryPermission() {
		assertEquals(EnumSet.allOf(Permission.class), Role.ADMIN.getPermissions());
	}

	@Test
	void forIdResolvesTheFourKnownRoles() {
		assertSame(Role.PARTICIPANT, Role.forId("participant"));
		assertSame(Role.EDITOR, Role.forId("editor"));
		assertSame(Role.ADMIN, Role.forId("admin"));
		assertSame(Role.SUPERVISOR, Role.forId("supervisor"));
	}

	@Test
	void supervisorIsExactlyParticipantPlusReadOnlyDelegation() {
		Set<Permission> expected = EnumSet.copyOf(Role.PARTICIPANT.getPermissions());
		expected.add(Permission.USER_DELEGATE_READ);
		assertEquals(expected, Role.SUPERVISOR.getPermissions());
	}

	@Test
	void supervisorMayNeverActOnSomeonesBehalfOrAdminister() {
		assertFalse(Role.SUPERVISOR.allows(Permission.USER_DELEGATE));
		assertFalse(Role.SUPERVISOR.allows(Permission.USER_LIST));
		assertFalse(Role.SUPERVISOR.allows(Permission.DIALOGUE_AUTHOR));
		assertFalse(Role.SUPERVISOR.allows(Permission.DIALOGUE_TEST));
		assertFalse(Role.SUPERVISOR.allows(Permission.PROJECT_READ));
	}

	@Test
	void onlySupervisorAndAdminMayDelegateReads() {
		assertTrue(Role.SUPERVISOR.allows(Permission.USER_DELEGATE_READ));
		assertTrue(Role.ADMIN.allows(Permission.USER_DELEGATE_READ));
		assertFalse(Role.PARTICIPANT.allows(Permission.USER_DELEGATE_READ));
		assertFalse(Role.EDITOR.allows(Permission.USER_DELEGATE_READ));
	}

	@Test
	void forIdReturnsNullForAnUnknownOrNullId() {
		assertNull(Role.forId("account"));
		assertNull(Role.forId(""));
		assertNull(Role.forId(null));
	}

	@Test
	void permissionsForRolesUnionsTheRecognisedRoles() {
		assertEquals(Role.EDITOR.getPermissions(),
				Role.permissionsForRoles(new String[] {"participant", "editor"}));
	}

	@Test
	void permissionsForRolesIgnoresUnknownRolesNullAndEmpty() {
		assertTrue(Role.permissionsForRoles(new String[] {"account", "offline_access"}).isEmpty());
		assertTrue(Role.permissionsForRoles(new String[0]).isEmpty());
		assertTrue(Role.permissionsForRoles(null).isEmpty());
	}

	@Test
	void permissionsForRolesKeepsKnownRolesWhenMixedWithUnknownOnes() {
		assertEquals(Role.EDITOR.getPermissions(),
				Role.permissionsForRoles(new String[] {"editor", "uma_authorization"}));
	}

	@Test
	void aRolesPermissionSetIsUnmodifiable() {
		Set<Permission> permissions = Role.ADMIN.getPermissions();
		assertThrows(UnsupportedOperationException.class,
				() -> permissions.add(Permission.DIALOGUE_RUN));
	}

	@Test
	void permissionsForRolesReturnsACallerOwnedSet() {
		Set<Permission> permissions = Role.permissionsForRoles(new String[] {"participant"});
		permissions.clear(); // must not throw, and must not affect Role.PARTICIPANT
		assertFalse(Role.PARTICIPANT.getPermissions().isEmpty());
	}
}
