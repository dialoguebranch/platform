/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

import com.dialoguebranch.execution.VariableUpdatedSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * JPA entity representing a single Dialogue Branch Variable stored in the {@code variables}
 * database table. Each variable belongs to a {@link DBUser} and stores the full state of a
 * {@link com.dialoguebranch.execution.Variable}: name, JSON-serialized value, last-updated
 * timestamp (epoch milliseconds), timezone (IANA string), and update source.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "variables",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "user_name",
			columnNames = { "user_id", "name" }
		)
	})
public class DBVariable {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private DBUser user;

	private String name;

	private String value;

	@Column(name = "updated_time")
	private Long updatedTime;

	@Column(name = "updated_time_zone")
	private String updatedTimeZone;

	@Enumerated(EnumType.STRING)
	@Column(name = "updated_source")
	private VariableUpdatedSource updatedSource;

	/**
	 * Creates an empty instance of {@link DBVariable}.
	 */
	public DBVariable() {
	}

	/**
	 * Creates an instance of {@link DBVariable} with the given name and JSON-serialized value.
	 *
	 * @param name the variable name.
	 * @param value the variable value as a JSON string.
	 */
	public DBVariable(String name, String value) {
		this.name = name;
		this.value = value;
	}

	/**
	 * Returns the unique UUID identifier of this variable.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this variable.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBUser} that owns this variable.
	 *
	 * @return the owning user.
	 */
	public DBUser getUser() {
		return user;
	}

	/**
	 * Sets the {@link DBUser} that owns this variable.
	 *
	 * @param user the owning user.
	 */
	public void setUser(DBUser user) {
		this.user = user;
	}

	/**
	 * Returns the name of this variable.
	 *
	 * @return the variable name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this variable.
	 *
	 * @param name the variable name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the JSON-serialized value of this variable.
	 *
	 * @return the value as a JSON string.
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Sets the JSON-serialized value of this variable.
	 *
	 * @param value the value as a JSON string.
	 */
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * Returns the timestamp of when this variable was last updated (as epoch time in milliseconds).
	 *
	 * @return the last-updated timestamp in epoch milliseconds, or {@code null} if unknown.
	 */
	public Long getUpdatedTime() {
		return updatedTime;
	}

	/**
	 * Sets the timestamp of when this variable was last updated (as epoch time in milliseconds).
	 *
	 * @param updatedTime the last-updated timestamp in epoch milliseconds.
	 */
	public void setUpdatedTime(Long updatedTime) {
		this.updatedTime = updatedTime;
	}

	/**
	 * Returns the time zone in which this variable was last updated (as IANA string,
	 * e.g. "Europe/Lisbon").
	 *
	 * @return the IANA time zone string, or {@code null} if unknown.
	 */
	public String getUpdatedTimeZone() {
		return updatedTimeZone;
	}

	/**
	 * Sets the time zone in which this variable was last updated (as IANA string,
	 * e.g. "Europe/Lisbon").
	 *
	 * @param updatedTimeZone the IANA time zone string.
	 */
	public void setUpdatedTimeZone(String updatedTimeZone) {
		this.updatedTimeZone = updatedTimeZone;
	}

	/**
	 * Returns the {@link VariableUpdatedSource} indicating what caused the last update to this
	 * variable.
	 *
	 * @return the update source, or {@code null} if unknown.
	 */
	public VariableUpdatedSource getUpdatedSource() {
		return updatedSource;
	}

	/**
	 * Sets the {@link VariableUpdatedSource} indicating what caused the last update to this
	 * variable.
	 *
	 * @param updatedSource the update source.
	 */
	public void setUpdatedSource(VariableUpdatedSource updatedSource) {
		this.updatedSource = updatedSource;
	}

}
