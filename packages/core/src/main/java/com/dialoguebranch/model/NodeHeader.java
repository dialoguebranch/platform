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

package com.dialoguebranch.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the header section of a Dialogue Branch node. Each node in a dialogue script begins
 * with a header that identifies the node by its {@code title}, optionally names the {@code speaker}
 * delivering the node's content, and may carry arbitrary key-value metadata via
 * {@code optionalTags}.
 *
 * @author Fruit Tree Labs
 */
public class NodeHeader {

	/** The unique title that identifies this node within its dialogue. */
	private String title;

	/** The name of the speaker delivering this node's content, or {@code null} if unspecified. */
	private String speaker;

	/**
	 * Arbitrary key-value metadata tags declared in the node header beyond the standard
	 * {@code title} and {@code speaker} fields. Insertion order is preserved.
	 */
	private Map<String,String> optionalTags;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an empty {@link NodeHeader} with no title, no speaker, and an empty optional-tags
	 * map.
	 */
	public NodeHeader() {
		optionalTags = new LinkedHashMap<>();
	}

	/**
	 * Creates a {@link NodeHeader} with the given {@code title} and an empty optional-tags map.
	 *
	 * @param title the unique title identifying this node within its dialogue
	 */
	public NodeHeader(String title) {
		this.title = title;
		optionalTags = new LinkedHashMap<>();
	}

	/**
	 * Creates a {@link NodeHeader} with the given {@code title} and {@code optionalTags} map.
	 *
	 * @param title the unique title identifying this node within its dialogue
	 * @param optionalTags a map of additional key-value metadata declared in the header
	 */
	public NodeHeader(String title, Map<String,String> optionalTags) {
		this.title = title;
		this.optionalTags = optionalTags;
	}

	/**
	 * Creates a deep copy of the given {@link NodeHeader}.
	 *
	 * @param other the {@link NodeHeader} to copy
	 */
	public NodeHeader(NodeHeader other) {
		this.title = other.title;
		this.speaker = other.speaker;
		this.optionalTags = new LinkedHashMap<>(other.optionalTags);
	}

	// ------------------------------------------------- //
	// -------------------- Getters -------------------- //
	// ------------------------------------------------- //

	/**
	 * Returns the title that uniquely identifies this node within its dialogue.
	 *
	 * @return the node title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Returns the name of the speaker delivering this node's content, or {@code null} if no
	 * speaker is specified.
	 *
	 * @return the speaker name, or {@code null}
	 */
	public String getSpeaker() {
		return this.speaker;
	}

	/**
	 * Returns the map of optional key-value metadata tags declared in this node header. The
	 * returned map is the live backing map; modifications affect this header directly.
	 *
	 * @return the optional tags map, never {@code null}
	 */
	public Map<String,String> getOptionalTags() {
		return optionalTags;
	}

	// ------------------------------------------------- //
	// -------------------- Setters -------------------- //
	// ------------------------------------------------- //

	/**
	 * Sets the title that uniquely identifies this node within its dialogue.
	 *
	 * @param title the node title
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Sets the name of the speaker delivering this node's content. Pass {@code null} to indicate
	 * no specific speaker.
	 *
	 * @param speaker the speaker name, or {@code null}
	 */
	public void setSpeaker(String speaker) {
		this.speaker = speaker;
	}

	/**
	 * Replaces the optional-tags map with the given map.
	 *
	 * @param optionalTags the new optional tags map; must not be {@code null}
	 */
	public void setOptionalTags(Map<String,String> optionalTags) {
		this.optionalTags = optionalTags;
	}

	// ------------------------------------------------- //
	// -------------------- Utility -------------------- //
	// ------------------------------------------------- //

	/**
	 * Adds a single key-value metadata tag to this node header's optional-tags map, replacing any
	 * existing entry with the same {@code key}.
	 *
	 * @param key   the tag name
	 * @param value the tag value
	 */
	public void addOptionalTag(String key, String value) {
		optionalTags.put(key,value);
	}

	/**
	 * Returns a human-readable representation of this node header. The output always begins with
	 * the {@code title} line, followed by the {@code speaker} line (if set), and then one line per
	 * optional tag in insertion order.
	 *
	 * @return a multi-line string representation of this header
	 */
	public String toString() {
		String newline = System.getProperty("line.separator");
		StringBuilder result = new StringBuilder();
		result.append("title: " + title);
		if (speaker != null)
			result.append(newline + "speaker: " + speaker);
		for (String key : optionalTags.keySet()) {
			String value = optionalTags.get(key);
			result.append(newline + key + ": " + value);
		}
		return result.toString();
	}

}
