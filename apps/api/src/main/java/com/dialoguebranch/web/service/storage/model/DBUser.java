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

package com.dialoguebranch.web.service.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity representing a user record in the {@code users} database table. Each user is
 * identified by a UUID primary key and, as its stable business key, the pair
 * {@code (issuer, subject)} taken from the authenticating JWT (the OIDC {@code iss} and
 * {@code sub} claims — see issue #128). {@code username} is a mutable, non-key display/log
 * attribute refreshed from the token's {@code preferred_username}. A user may own a set of
 * {@link DBVariable} records.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "users",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_users_issuer_subject",
			columnNames = { "issuer", "subject" }
		)
	}
)
public class DBUser {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** The token issuer (the full OIDC {@code iss} URL) this user authenticates through. */
	@Column(nullable = false)
	private String issuer;

	/** The stable OIDC {@code sub} claim identifying this user within {@link #issuer}. */
	@Column(nullable = false)
	private String subject;

	/** The last-seen {@code preferred_username}; display/log only, not an identity key. */
	private String username;

	@OneToMany(mappedBy = "user")
	@JsonIgnore
	private Set<DBVariable> variables = new HashSet<>();

	/**
	 * Creates an empty instance of {@link DBUser}.
	 */
	public DBUser() {
	}

	/**
	 * Creates an instance of {@link DBUser} identified by {@code (issuer, subject)}.
	 *
	 * @param issuer the token issuer (full OIDC {@code iss} URL).
	 * @param subject the OIDC {@code sub} claim.
	 * @param username the last-seen {@code preferred_username}, or {@code null} if not known yet
	 *                 (e.g. a row created for a delegated user who has not authenticated directly).
	 */
	public DBUser(String issuer, String subject, String username) {
		this.issuer = issuer;
		this.subject = subject;
		this.username = username;
	}

	/**
	 * Returns the unique UUID identifier of this user.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this user.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the token issuer (full OIDC {@code iss} URL) this user authenticates through.
	 *
	 * @return the issuer.
	 */
	public String getIssuer() {
		return issuer;
	}

	/**
	 * Sets the token issuer (full OIDC {@code iss} URL) this user authenticates through.
	 *
	 * @param issuer the issuer.
	 */
	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	/**
	 * Returns the OIDC {@code sub} claim identifying this user within its {@link #getIssuer() issuer}.
	 *
	 * @return the subject.
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 * Sets the OIDC {@code sub} claim identifying this user within its issuer.
	 *
	 * @param subject the subject.
	 */
	public void setSubject(String subject) {
		this.subject = subject;
	}

	/**
	 * Returns the username of this user.
	 *
	 * @return the username.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Sets the username of this user.
	 *
	 * @param username the username.
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Returns the set of {@link DBVariable} records owned by this user.
	 *
	 * @return the set of variables.
	 */
	public Set<DBVariable> getVariables() {
		return variables;
	}

	/**
	 * Sets the set of {@link DBVariable} records owned by this user.
	 *
	 * @param variables the set of variables.
	 */
	public void setVariables(Set<DBVariable> variables) {
		this.variables = variables;
	}
}
