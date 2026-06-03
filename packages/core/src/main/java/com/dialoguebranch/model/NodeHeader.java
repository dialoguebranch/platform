/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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

public class NodeHeader {
	
	private String title;
	private String speaker;
	private Map<String,String> optionalTags;
	
	// ---------- Constructors:
	
	public NodeHeader() {
		optionalTags = new LinkedHashMap<>();
	}
	
	public NodeHeader(String title) {
		this.title = title;
		optionalTags = new LinkedHashMap<>();
	}
	
	public NodeHeader(String title, Map<String,String> optionalTags) {
		this.title = title;
		this.optionalTags = optionalTags;
	}

	public NodeHeader(NodeHeader other) {
		this.title = other.title;
		this.speaker = other.speaker;
		this.optionalTags = new LinkedHashMap<>(other.optionalTags);
	}
	
	// ---------- Getters:
	
	public String getTitle() {
		return title;
	}
	
	public String getSpeaker() {
		return this.speaker;
	}
	
	public Map<String,String> getOptionalTags() {
		return optionalTags;
	}
	
	// ---------- Setters:
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public void setSpeaker(String speaker) {
		this.speaker = speaker;
	}
	
	public void setOptionalTags(Map<String,String> optionalTags) {
		this.optionalTags = optionalTags;
	}
	
	// ---------- Utility:
	
	public void addOptionalTag(String key, String value) {
		optionalTags.put(key,value);
	}
	
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
