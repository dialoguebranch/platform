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
package com.dialoguebranch.execution;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * An abstract representation of a change in the {@link VariableStore}.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public abstract class VariableStoreChange {

	/**
	 * Defines a set of possible source of changes to the VariableStore.
	 */
	public enum Source {
		/** In case the source of the variable change is unknown. */
		UNKNOWN,

		/** In case the variable was changed through a SET-statement from within the script. */
		DLB_SCRIPT,

		/** In case a variable was changed through provided user input in a reply. */
		INPUT_REPLY,

		/** In case a change originated from the Dialogue Branch Web Service. */
		WEB_SERVICE,

		/** In case the change was caused through an External Variable Service. */
		EXTERNAL_VARIABLE_SERVICE
	}

	private final ZonedDateTime time;
	private final Source source;

	/**
	 * Creates an instance of a {@link VariableStoreChange} with a given {@code time} and {@code
	 * source}.
	 *
	 * @param time the time that this change took place (in the time zone of the user).
	 * @param source the source of the {@link VariableStoreChange}, as one of {@link Source}.
	 */
	public VariableStoreChange(ZonedDateTime time, Source source) {
		this.time = time;
		this.source = source;
	}

	/**
	 * Returns the {@link Source} of this change to the VariableStore.
	 *
	 * @return the {@link Source} of this change to the VariableStore.
	 */
	public Source getSource() {
		return source;
	}

	/**
	 * Returns the time that this change took place (in the time zone of the user).
	 *
	 * @return the time that this change took place (in the time zone of the user).
	 */
	public ZonedDateTime getTime() {
		return time;
	}

	/**
	 * An implementation of {@link VariableStoreChange} representing a set of added {@link
	 * Variable}s.
	 *
	 * @author Dennis Hofs (Roessingh Research and Development)
	 * @author Harm op den Akker (Fruit Tree Labs)
	 */
	public static class Put extends VariableStoreChange {
		private final Map<String,Object> variables;

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a given map of
		 * {@link Variable}s.
		 * @param variablesMap the mapping from variable name to {@link Variable} that was
		 *                         added in this {@link VariableStoreChange}.
		 * @param time the time that this change took place (in the time zone of the user).
		 */
		public Put(Map<String, Variable> variablesMap, ZonedDateTime time) {
			super(time,Source.UNKNOWN);
			variables = new LinkedHashMap<>();
			for(Variable Variable : variablesMap.values()) {
				variables.put(Variable.getName(), Variable.getValue());
			}
		}

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a given map of
		 * {@link Variable}s.
		 * @param variablesMap the mapping from variable name to {@link Variable} that was
		 *                         added in this {@link VariableStoreChange}.
		 * @param time the time that this change took place (in the time zone of the user).
		 * @param source the source of the change to the variable store.
		 */
		public Put(Map<String, Variable> variablesMap, ZonedDateTime time, Source source) {
			super(time,source);
			variables = new LinkedHashMap<>();
			for(Variable Variable : variablesMap.values()) {
				variables.put(Variable.getName(), Variable.getValue());
			}
		}

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a given
		 * {@code variableName}, {@code variableValues}, and {@code lastUpdated} timestamp in the
		 * timezone of the DialogueBranch user.
		 * @param variableName the name of the {@link Variable} representing this Put change.
		 * @param variableValue the value of the {@link Variable} representing this Put change.
		 * @param time the time that this change took place (in the time zone of the user).
		 */
		public Put(String variableName, Object variableValue, ZonedDateTime time) {
			super(time, Source.UNKNOWN);
			variables = new LinkedHashMap<>();
			variables.put(variableName, variableValue);
		}

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a given
		 * {@code variableName}, {@code variableValues}, and {@code lastUpdated} timestamp in the
		 * timezone of the DialogueBranch user.
		 * @param variableName the name of the {@link Variable} representing this Put change.
		 * @param variableValue the value of the {@link Variable} representing this Put change.
		 * @param time the time that this change took place (in the time zone of the user).
		 * @param source the source of the change to the variable store.
		 */
		public Put(String variableName, Object variableValue, ZonedDateTime time, Source source) {
			super(time, source);
			variables = new LinkedHashMap<>();
			variables.put(variableName, variableValue);
		}

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a single given
		 * {@link Variable}.
		 * @param variable the one and only {@link Variable} that was added in this
		 *                     {@link VariableStoreChange}.
		 * @param time the time that this change took place (in the time zone of the user).
		 */
		public Put(Variable variable, ZonedDateTime time) {
			super(time, Source.UNKNOWN);
			variables = new LinkedHashMap<>();
			variables.put(variable.getName(), variable.getValue());
		}

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a single given
		 * {@link Variable}.
		 * @param variable the one and only {@link Variable} that was added in this
		 *                     {@link VariableStoreChange}.
		 * @param time the time that this change took place (in the time zone of the user).
		 * @param source the source of the change to the variable store.
		 */
		public Put(Variable variable, ZonedDateTime time, Source source) {
			super(time, source);
			variables = new LinkedHashMap<>();
			variables.put(variable.getName(), variable.getValue());
		}

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a list of given
		 * {@link Variable}s.
		 * @param VariablesList the list of {@link Variable}s that were added in this
		 *                          {@link VariableStoreChange}.
		 * @param time the time that this change took place (in the time zone of the user).
		 */
		public Put(List<Variable> VariablesList, ZonedDateTime time) {
			super(time, Source.UNKNOWN);
			variables = new LinkedHashMap<>();
			for(Variable variable : VariablesList) {
				variables.put(variable.getName(), variable.getValue());
			}
		}

		/**
		 * Creates an instance of a {@link Put} {@link VariableStoreChange} with a list of given
		 * {@link Variable}s.
		 * @param VariablesList the list of {@link Variable}s that were added in this
		 *                          {@link VariableStoreChange}.
		 * @param time the time that this change took place (in the time zone of the user).
		 * @param source the source of the change to the variable store.
		 */
		public Put(List<Variable> VariablesList, ZonedDateTime time, Source source) {
			super(time, source);
			variables = new LinkedHashMap<>();
			for(Variable variable : VariablesList) {
				variables.put(variable.getName(), variable.getValue());
			}
		}

		/**
		 * Returns the mapping of variable name to value ({@link Object}) representing all the
		 * variables that have been added in this {@link VariableStoreChange}.
		 * @return the added DialogueBranch Variables.
		 */
		public Map<String,Object> getVariables() {
			return variables;
		}

	}

	/**
	 * An implementation of {@link VariableStoreChange} representing a set of removed DialogueBranch
	 * Variables, identified by their variable names.
	 *
	 * @author Dennis Hofs
	 * @author Harm op den Akker
	 */
	public static class Remove extends VariableStoreChange {
		private final Collection<String> removedVariableNames;

		/**
		 * Creates an instance of a {@link Remove} {@link VariableStoreChange} with a given
		 * collection of variableNames.
		 * @param variableNames the names of the variables that have been removed in this change.
		 * @param time the time that this change took place (in the time zone of the user).
		 */
		public Remove(Collection<String> variableNames, ZonedDateTime time) {
			super(time, Source.UNKNOWN);
			this.removedVariableNames = variableNames;
		}

		/**
		 * Creates an instance of a {@link Remove} {@link VariableStoreChange} with a given
		 * collection of variableNames.
		 * @param variableNames the names of the variables that have been removed in this change.
		 * @param time the time that this change took place (in the time zone of the user).
		 * @param source the source of the change to the variable store.
		 */
		public Remove(Collection<String> variableNames, ZonedDateTime time, Source source) {
			super(time, source);
			this.removedVariableNames = variableNames;
		}

		/**
		 * Creates an instance of a {@link Remove} {@link VariableStoreChange} with a given
		 * single variable name, representing the variable that was removed with this change.
		 * @param variableName the name of the variable that was removed with this change.
		 * @param time the time that this change took place (in the time zone of the user).
		 */
		public Remove(String variableName, ZonedDateTime time) {
			super(time, Source.UNKNOWN);
			removedVariableNames = Collections.singletonList(variableName);
		}

		/**
		 * Creates an instance of a {@link Remove} {@link VariableStoreChange} with a given
		 * single variable name, representing the variable that was removed with this change.
		 * @param variableName the name of the variable that was removed with this change.
		 * @param time the time that this change took place (in the time zone of the user).
		 * @param source the source of the change to the variable store.
		 */
		public Remove(String variableName, ZonedDateTime time, Source source) {
			super(time, source);
			removedVariableNames = Collections.singletonList(variableName);
		}

		/**
		 * Returns the collection of variable names that are associated with this {@link Remove}
		 * {@link VariableStoreChange}.
		 * @return the collection of variable names of variables that have been removed.
		 */
		public Collection<String> getVariableNames() {
			return removedVariableNames;
		}

	}

	/**
	 * An implementation of {@link VariableStoreChange} representing a full clear of the
	 * {@link VariableStore}.
	 *
	 * @author Dennis Hofs
	 * @author Harm op den Akker
	 */
	public static class Clear extends VariableStoreChange {

		/**
		 * Creates an instance of a {@link Clear} {@link VariableStoreChange} indicating a full
		 * clear (removed all variables) of the DialogueBranch Variable Store.
		 * @param time the time that this change took place (in the time zone of the user).
		 */
		public Clear(ZonedDateTime time) {
			super(time,Source.UNKNOWN);
		}

		/**
		 * Creates an instance of a {@link Clear} {@link VariableStoreChange} indicating a full
		 * clear (removed all variables) of the DialogueBranch Variable Store.
		 * @param time the time that this change took place (in the time zone of the user).
		 * @param source the source of the change to the variable store.
		 */
		public Clear(ZonedDateTime time, Source source) {
			super(time,source);
		}

	}

}
