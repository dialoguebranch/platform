/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.dialoguebranch.web.service.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A named set of {@link Permission}s. A user's roles come from the Keycloak JWT (see
 * {@link AuthenticationInfo#getRoles()}); the union of the matching roles' permissions is what
 * {@link AuthorizationService} tests an operation against.
 *
 * <p>The three roles are fixed constants, defined once here as the single place that decides what
 * each Keycloak role may do. They are layered: {@link #EDITOR} is {@link #PARTICIPANT} plus the
 * authoring permissions, and {@link #ADMIN} is {@link #EDITOR} plus the project-lifecycle and
 * service-administration permissions (so {@code ADMIN} currently grants every {@link Permission}).
 * Making roles configurable, or letting an administrator define new ones, is deliberately left for
 * later — see <a href="https://github.com/dialoguebranch/platform/issues/99">#99</a>.</p>
 *
 * @author Harm op den Akker
 */
public final class Role {

	/** May run dialogues and read/write its own variables and logs. */
	public static final Role PARTICIPANT;

	/** Everything {@link #PARTICIPANT} may do, plus authoring, draft-testing and publish inspection. */
	public static final Role EDITOR;

	/** Everything {@link #EDITOR} may do, plus project lifecycle, publishing and service administration. */
	public static final Role ADMIN;

	/** All defined roles, in ascending order of privilege. */
	private static final List<Role> ALL;

	static {
		EnumSet<Permission> participant = EnumSet.of(
				Permission.DIALOGUE_RUN,
				Permission.VARIABLE_READ_OWN,
				Permission.VARIABLE_WRITE_OWN,
				Permission.LOG_READ_OWN);

		EnumSet<Permission> editor = EnumSet.copyOf(participant);
		editor.addAll(EnumSet.of(
				Permission.DIALOGUE_LIST,
				Permission.PROJECT_READ,
				Permission.VARIABLE_INSPECT_PROJECT,
				Permission.DIALOGUE_AUTHOR,
				Permission.DIALOGUE_DRAFT_TEST,
				Permission.PUBLISH_READ));

		EnumSet<Permission> admin = EnumSet.copyOf(editor);
		admin.addAll(EnumSet.of(
				Permission.PROJECT_CREATE,
				Permission.PROJECT_UPDATE,
				Permission.PROJECT_DELETE,
				Permission.PROJECT_IMPORT_EXPORT,
				Permission.PROJECT_MANAGE_LANGUAGES,
				Permission.PUBLISH_CREATE,
				Permission.SERVICE_INFO_TECHNICAL,
				Permission.USER_LIST,
				Permission.USER_DELEGATE));

		PARTICIPANT = new Role(AuthenticationInfo.USER_ROLE_PARTICIPANT, participant);
		EDITOR = new Role(AuthenticationInfo.USER_ROLE_EDITOR, editor);
		ADMIN = new Role(AuthenticationInfo.USER_ROLE_ADMIN, admin);
		ALL = List.of(PARTICIPANT, EDITOR, ADMIN);
	}

	private final String roleId;
	private final Set<Permission> permissions;

	private Role(String roleId, Set<Permission> permissions) {
		this.roleId = roleId;
		this.permissions = Collections.unmodifiableSet(EnumSet.copyOf(permissions));
	}

	/**
	 * Returns this role's id, matching the value that appears in the JWT's
	 * {@code resource_access.dlb-web-service.roles} claim (e.g. {@code "editor"}).
	 *
	 * @return the role id.
	 */
	public String getRoleId() {
		return roleId;
	}

	/**
	 * Returns this role's permissions as an unmodifiable set.
	 *
	 * @return the permissions granted by this role.
	 */
	public Set<Permission> getPermissions() {
		return permissions;
	}

	/**
	 * Returns whether this role grants {@code permission}.
	 *
	 * @param permission the permission to test.
	 * @return {@code true} if this role grants it.
	 */
	public boolean allows(Permission permission) {
		return permissions.contains(permission);
	}

	/**
	 * Returns the {@link Role} with the given id, or {@code null} if {@code roleId} is {@code null}
	 * or does not name one of the defined roles (e.g. an unrelated Keycloak client role).
	 *
	 * @param roleId the role id to look up.
	 * @return the matching {@link Role}, or {@code null}.
	 */
	public static Role forId(String roleId) {
		if (roleId == null) {
			return null;
		}
		for (Role role : ALL) {
			if (role.roleId.equals(roleId)) {
				return role;
			}
		}
		return null;
	}

	/**
	 * Returns the union of the permissions granted by every recognised role in {@code roleIds}.
	 * Unrecognised ids and a {@code null} array are ignored, yielding an empty set.
	 *
	 * @param roleIds the role ids held by a user (typically {@link AuthenticationInfo#getRoles()}).
	 * @return the combined permissions, as a set that is safe for the caller to mutate.
	 */
	public static Set<Permission> permissionsForRoles(String[] roleIds) {
		EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
		if (roleIds == null) {
			return result;
		}
		for (String roleId : roleIds) {
			Role role = forId(roleId);
			if (role != null) {
				result.addAll(role.permissions);
			}
		}
		return result;
	}

}
