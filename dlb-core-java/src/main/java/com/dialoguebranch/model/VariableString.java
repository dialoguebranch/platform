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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.rrd.utils.expressions.Value;

/**
 * This class represents a text with possible variables. It is modelled as a list of segments, where
 * each segment is plain text or a variable.
 * 
 * <p>The segments are always normalized so that subsequent plain text segments are automatically
 * merged into one.</p>
 * 
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class VariableString {

	/** The list of {@link Segment}s that makes up this {@link VariableString}. */
	private final List<Segment> segments = new ArrayList<>();

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of an empty {@link VariableString}.
	 */
	public VariableString() {}

	/**
	 * Creates an instance of a {@link VariableString} from the given {@code text}.
	 *
	 * @param text a String of text with possible variables.
	 */
	public VariableString(String text) {
		segments.add(new TextSegment(text));
	}

	/**
	 * Creates an instance of a {@link VariableString} from the contents of the {@code other} given
	 * {@link VariableString}.
	 *
	 * @param other the other {@link VariableString} from which to copy its contents.
	 */
	public VariableString(VariableString other) {
		for (Segment segment : other.segments) {
			this.segments.add(segment.clone());
		}
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the segments as an unmodifiable list.
	 * 
	 * @return the segments as an unmodifiable list
	 */
	public List<Segment> getSegments() {
		return Collections.unmodifiableList(segments);
	}

	/**
	 * Adds the given {@link Segment} to the list of segments for this {@link VariableString}. If
	 * both the given {@code segment} and the last Segment in the current list are of type {@link
	 * TextSegment}, they will be merged into a single segment.
	 *
	 * @param segment the {@link Segment} to add.
	 */
	public void addSegment(Segment segment) {
		Segment lastSegment = null;
		if (!segments.isEmpty())
			lastSegment = segments.get(segments.size() - 1);
		if (lastSegment instanceof TextSegment lastTextSegment &&
                segment instanceof TextSegment textSegment) {
            TextSegment mergedSegment = new TextSegment(lastTextSegment.text + textSegment.text);
			segments.remove(segments.size() - 1);
			segments.add(mergedSegment);
		} else {
			segments.add(segment);
		}
	}

	/**
	 * Adds the given Set of {@link Segment}s to this {@link VariableString}.
	 *
	 * @param segments the segments to add.
	 */
	public void addSegments(Iterable<Segment> segments) {
		for (Segment segment : segments) {
			addSegment(segment);
		}
	}

	/**
	 * Executes this variable string with respect to the specified variables. The result will be a
	 * string with 0 or 1 text segments. Undefined variables will be evaluated as string "null".
	 * 
	 * @param variables the variable map (can be {@code null}).
	 * @return the processed variable string.
	 */
	public VariableString execute(Map<String,Object> variables) {
		VariableString result = new VariableString();
		for (Segment segment : segments) {
			if (segment instanceof TextSegment) {
				result.addSegment(segment);
			} else {
				VariableSegment varSegment = (VariableSegment)segment;
				Object valueObj = null;
				if (variables != null)
					valueObj = variables.get(varSegment.variableName);
				Value value = new Value(valueObj);
				result.addSegment(new TextSegment(value.toString()));
			}
		}
		return result;
	}

	/**
	 * Evaluates this variable string with respect to the specified variables. Undefined variables
	 * will be evaluated as string "null".
	 * 
	 * @param variables the variable map (can be {@code null}).
	 * @return the evaluated string.
	 */
	public String evaluate(Map<String,Object> variables) {
		VariableString variableString = execute(variables);
		if (variableString.segments.isEmpty())
			return "";
		TextSegment segment = (TextSegment)variableString.segments.get(0);
		return segment.text;
	}
	
	/**
	 * Retrieves all variable names that are read in this variable string and adds them to the
	 * specified set.
	 * 
	 * @param variableNames the set to which the variable names are added.
	 */
	public void getReadVariableNames(Set<String> variableNames) {
		for (Segment segment : segments) {
			if (!(segment instanceof VariableSegment variableSegment))
				continue;
            variableNames.add(variableSegment.variableName);
		}
	}

	/**
	 * Checks whether there are any 'real' contents in this {@link VariableString}. If all of its
	 * segments are {@link TextSegment}s that contain only whitespace, this method returns {@code
	 * false}, otherwise it returns {@code true}.
	 *
	 * @return {@code true} if this {@link VariableString} has any non-whitespace contents.
	 */
	public boolean hasContents() {
		for (Segment segment : segments) {
			if (!(segment instanceof TextSegment textSegment))
				return true;
            if (!textSegment.text.trim().isEmpty())
				return true;
		}
		return false;
	}

	/**
	 * Checks whether this {@link VariableString} contains any variables. Returns {@code true} if it
	 * does and {@code false} if there are only {@link TextSegment}s.
	 *
	 * @return {@code true} if this {@link VariableString} contains any variables.
	 */
	public boolean containsVariables() {
		for (Segment segment : segments) {
			if (!(segment instanceof TextSegment))
				return true;
		}
		return false;
	}

	/**
	 * Remove all (leading and trailing) white space from this {@link VariableString}.
	 */
	public void trimWhitespace() {
		removeLeadingWhitespace();
		removeTrailingWhitespace();
	}

	/**
	 * Removes all white space from this {@link VariableString}'s segments until it encounters text
	 * or a variable.
	 */
	public void removeLeadingWhitespace() {
		while (!segments.isEmpty()) {
			Segment segment = segments.get(0);
			if (!(segment instanceof TextSegment textSegment))
				return;
            String content = textSegment.getText().replaceAll("^\\s+", "");
			textSegment.setText(content);
			if (!content.isEmpty())
				return;
			segments.remove(0);
		}
	}

	/**
	 * Removes all white space at the end of this {@link VariableString}s contents, until it
	 * encounters text or a variable.
	 */
	public void removeTrailingWhitespace() {
		while (!segments.isEmpty()) {
			Segment segment = segments.get(segments.size() - 1);
			if (!(segment instanceof TextSegment textSegment))
				return;
            String content = textSegment.getText().replaceAll("\\s+$", "");
			textSegment.setText(content);
			if (!content.isEmpty())
				return;
			segments.remove(segments.size() - 1);
		}
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		for (Segment segment : segments) {
			result.append(segment.toString());
		}
		return result.toString();
	}
	
	/**
	 * Returns the code string for this instance. It will escape \ and $ with a backslash. You may
	 * specify additional characters to escape.
	 * 
	 * @param escapes the characters to escape
	 * @return the code string
	 */
	public String toString(char[] escapes) {
		StringBuilder result = new StringBuilder();
		for (Segment segment : segments) {
			if (segment instanceof TextSegment) {
				result.append(((TextSegment)segment).toString(escapes));
			} else {
				result.append(segment.toString());
			}
		}
		return result.toString();
	}

	/**
	 * A {@link Segment} represents a piece of a {@link VariableString} that can either be a {@link
	 * TextSegment} or a {@link VariableSegment}.
	 */
	public static abstract class Segment implements Cloneable {

		/**
		 * Creates an instance of the implementing subclass.
		 */
		public Segment() { }

		@Override
		public abstract Segment clone();
	}

	/**
	 * A {@link TextSegment} represents a piece of a {@link VariableString} that only contains
	 * regular text.
	 */
	public static class TextSegment extends Segment {
		private String text;

		/**
		 * Creates an instance of a {@link TextSegment} with the given {@code text}.
		 * @param text the text contents for this {@link TextSegment}.
		 */
		public TextSegment(String text) {
			this.text = text;
		}

		/**
		 * Creates an instance of a {@link TextSegment} with the contents of the {@code other}
		 * {@link TextSegment}.
		 *
		 * @param other the {@link TextSegment} from which to copy its contents.
		 */
		public TextSegment(TextSegment other) {
			this.text = other.text;
		}

		/**
		 * Returns the text contents of this {@link TextSegment}.
		 * @return the text contents of this {@link TextSegment}.
		 */
		public String getText() {
			return text;
		}

		/**
		 * Sets the text contents of this {@link TextSegment}.
		 * @param text the text contents of this {@link TextSegment}.
		 */
		public void setText(String text) {
			this.text = text;
		}
		
		@Override
		public String toString() {
			return text.replaceAll("\\\\", "\\\\\\\\")
					.replaceAll("\\$", "\\\\\\$");
		}
		
		/**
		 * Returns the code string for this instance. It will escape \ and $
		 * with a backslash. You may specify additional characters to escape.
		 * 
		 * @param escapes the characters to escape
		 * @return the code string
		 */
		public String toString(char[] escapes) {
			String input = toString();
			StringBuilder builder = new StringBuilder();
			int start = 0;
			for (int i = 0; i < input.length(); i++) {
				char c = input.charAt(i);
				for (char escape : escapes) {
					if (c == escape) {
						builder.append(input, start, i);
						builder.append('\\');
						builder.append(c);
						start = i + 1;
						break;
					}
				}
			}
			if (start < input.length())
				builder.append(input.substring(start));
			return builder.toString();
		}

		@Override
		public TextSegment clone() {
			return new TextSegment(this);
		}
	}

	/**
	 * A {@link VariableSegment} represents a piece of a {@link VariableString} that contains a
	 * named variable.
	 */
	public static class VariableSegment extends Segment {

		/** The name of the variable in this {@link VariableSegment}. */
		private String variableName;

		/**
		 * Creates an instance of a {@link VariableSegment} with a given {@code variableName}.
		 *
		 * @param variableName the name of the variable.
		 */
		public VariableSegment(String variableName) {
			this.variableName = variableName;
		}

		/**
		 * Creates an instance of a {@link VariableSegment} given the contents of an {@code other}
		 * {@link VariableSegment}.
		 *
		 * @param other the {@link VariableSegment} from which to copy its contents.
		 */
		public VariableSegment(VariableSegment other) {
			this.variableName = other.variableName;
		}

		/**
		 * Returns the variable name of this {@link VariableSegment}.
		 * @return the variable name of this {@link VariableSegment}.
		 */
		public String getVariableName() {
			return variableName;
		}

		/**
		 * Sets the variable name of this {@link VariableSegment}.
		 * @param variableName the variable name of this {@link VariableSegment}.
		 */
		public void setVariableName(String variableName) {
			this.variableName = variableName;
		}

		@Override
		public String toString() {
			return "$" + variableName;
		}

		@Override
		public VariableSegment clone() {
			return new VariableSegment(this);
		}
	}
}
