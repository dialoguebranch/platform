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
 * A {@link VariableStore} is an object that stores all Dialogue Branch variable values for a given
 * {@link User}.
 * 
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class VariableStore {

	/** Contains the list of all Variables in this store. */
	private final Map<String, Variable> variables = new HashMap<>();

	/** The Dialogue Branch user associated with this VariableStore. */
	private User user;

	/** Contains the list of all listeners that need to be notified for updates. */
	private final List<VariableStoreOnChangeListener> onChangeListeners = new ArrayList<>();

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of a new {@link VariableStore} for a given {@link User}.
	 *
	 * @param user the {@link User} associated with this {@link VariableStore}.
	 */
	public VariableStore(User user) {
		this.user = user;
	}

	/**
	 * Creates an instance of a new {@link VariableStore} for the given {@code user}, initialized
	 * with the given array of {@link Variable}s.
	 *
	 * @param user the {@link User} associated with this {@link VariableStore}.
	 * @param variableArray the array of variables used to initiate this {@link VariableStore}.
	 */
	public VariableStore(User user, Variable[] variableArray) {
		this.user = user;
		synchronized(variables) {
			for (Variable variable : variableArray) {
				variables.put(variable.getName(),variable);
			}
		}
	}

	// ----------------------------------------------------------
	// -------------------- Listener Methods --------------------
	// ----------------------------------------------------------

	/**
	 * Adds the given {@link VariableStoreOnChangeListener} to the list of listeners for this {@link
	 * VariableStore}.
	 *
	 * @param listener a {@link VariableStoreOnChangeListener} that should be notified whenever this
	 *                 {@link VariableStore} is changed
	 */
	public void addOnChangeListener(VariableStoreOnChangeListener listener) {
		synchronized (onChangeListeners) {
			onChangeListeners.add(listener);
		}
	}

	/**
	 * Removes the given {@link VariableStoreOnChangeListener} from the list of listeners for this
	 * {@link VariableStore}.
	 *
	 * @param listener a {@link VariableStoreOnChangeListener} that was previously registered
	 *                 to listen for changes.
	 * @return {@code true} if the given {@link VariableStoreOnChangeListener} was removed, or
	 *         {@code false} otherwise (e.g. if it was not registered as a listener to begin with).
	 */
	public boolean removeOnChangeListener(VariableStoreOnChangeListener listener) {
		synchronized (onChangeListeners) {
			return onChangeListeners.remove(listener);
		}
	}

	/**
	 * Notifies all {@link VariableStoreOnChangeListener} that are listening for changes to this
	 * {@link VariableStore} of one or more changes as represented by the list of {@link
	 * VariableStoreChange} {@code changes}.
	 *
	 * @param changes one or multiple {@link VariableStoreChange}s representing a modification
	 *                to this {@link VariableStore}.
	 */
	private void notifyOnChange(VariableStoreChange... changes) {
		List<VariableStoreOnChangeListener> listeners;
		synchronized (onChangeListeners) {
			listeners = new ArrayList<>(onChangeListeners);
		}
		for (VariableStoreOnChangeListener listener : listeners) {
			listener.onChange(this, Arrays.asList(changes));
		}
	}

	// -----------------------------------------------------------
	// -------------------- Retrieval Methods --------------------
	// -----------------------------------------------------------

	/**
	 * Retrieves the variable identified by the given {@code name}, or returns {@code null} if no
	 * such variable is known in this {@link VariableStore}.
	 *
	 * @param name the name of the variable to retrieve.
	 * @return the {@link Variable} with the given {@code name}, nor {@code null}.
	 */
	public Variable getVariable(String name) {
		synchronized (variables) {
			return variables.get(name);
		}
	}

	/**
	 * Returns the contents of this {@link VariableStore} as an array of {@link Variable}s.
	 *
	 * @return the contents of this {@link VariableStore} as an array of {@link Variable}s.
	 */
	public Variable[] getVariables() {
		synchronized (variables) {
			return variables.values().toArray(new Variable[0]);
		}
	}

	/**
	 * Returns the value of the variable identified by the given {@code name}. If no such variable
	 * is known in this {@link VariableStore}, then this method returns null.
	 *
	 * <p>Note: if this method returns null, it can mean that the variable does not exist, or that
	 * the variable has value {@code null}. If you need to distinguish these two cases, you should
	 * call {@link #getVariable(String)}.</p>
	 *
	 * @param variableName the name of the variable to retrieve.
	 * @return the value of the variable, null if the variable does not exist or the variable value
	 *         is {@code null}.
	 */
	public Object getValue(String variableName) {
		Variable variable;
		synchronized (variables) {
			variable = variables.get(variableName);
		}
		if (variable == null)
			return null;
		return variable.getValue();
	}

	/**
	 * Returns the {@link User} associated with this {@link VariableStore}.
	 *
	 * @return the {@link User} associated with this {@link VariableStore}.
	 */
	public User getUser() {
		return user;
	}

	/**
	 * Returns a set of all the names of {@link Variable}s contained in this {@link VariableStore}.
	 *
	 * @return a set of all the names of {@link Variable}s contained in this {@link VariableStore}.
	 */
	public Set<String> getVariableNames() {
		return variables.keySet();
	}

	/**
	 * Get a sorted list of all variable names in this {@link VariableStore}.
	 *
	 * @return a sorted list of all variable names in this {@link VariableStore}.
	 */
	public List<String> getSortedVariableNames() {
		List<String> nameList = new ArrayList<>(variables.keySet());
		Collections.sort(nameList);
		return nameList;
	}

	// --------------------------------------------------------------
	// -------------------- Modification Methods --------------------
	// --------------------------------------------------------------

	/**
	 * Stores the given {@code value} under the given variable-{@code name} in this
	 * {@link VariableStore} and sets the updatedTime to {@code updatedTime}.
	 *
	 * @param name the name of the variable to store.
	 * @param value the value of the variable to store.
	 * @param notifyObservers true if observers of this {@link VariableStore} should be
	 *                        notified about this update.
	 * @param eventTime the time (in the time zone of the user) of the event that triggered the
	 *                  update of this variable.
	 */
	public void setValue(String name, Object value, boolean notifyObservers,
						 ZonedDateTime eventTime) {
		setValue(name,value,notifyObservers,eventTime, VariableStoreChange.Source.UNKNOWN);
	}

	/**
	 * Stores the given {@code value} under the given variable-{@code name} in this
	 * {@link VariableStore} and sets the updatedTime to {@code updatedTime}.
	 *
	 * @param name the name of the variable to store.
	 * @param value the value of the variable to store.
	 * @param notifyObservers true if observers of this {@link VariableStore} should be
	 *                        notified about this update.
	 * @param eventTime the time (in the time zone of the user) of the event that triggered the
	 *                  update of this variable.
	 * @param source the source of the update to this {@link VariableStore}.
	 */
	public void setValue(String name, Object value, boolean notifyObservers,
						 ZonedDateTime eventTime, VariableStoreChange.Source source) {
		synchronized (variables) {
			Variable Variable = new Variable(name, value, eventTime);
			variables.put(name, Variable);
			if (notifyObservers) {
				notifyOnChange(new VariableStoreChange.Put(Variable, eventTime, source));
			}
		}
	}

	/**
	 * Remove the {@link Variable} with the given {@code name} from this
	 * {@link VariableStore}. This method returns the {@link Variable} object that has been
	 * deleted, or {@code null} if the element to be deleted was not found.
	 *
	 * @param name the name of the {@link Variable} to remove.
	 * @param notifyObservers true if observers of this {@link VariableStore} should be
	 *                        notified about this update.
	 * @param eventTime the time (in the time zone of the user) of the event that triggered the
	 *                  removal of this variable
	 * @return the {@link Variable} that was removed, or {@code null}.
	 */
	public Variable removeByName(String name, boolean notifyObservers,
                                 ZonedDateTime eventTime) {
		return removeByName(name,notifyObservers,eventTime, VariableStoreChange.Source.UNKNOWN);
	}

	/**
	 * Remove the {@link Variable} with the given {@code name} from this
	 * {@link VariableStore}. This method returns the {@link Variable} object that has been
	 * deleted, or {@code null} if the element to be deleted was not found.
	 *
	 * @param name the name of the {@link Variable} to remove.
	 * @param notifyObservers true if observers of this {@link VariableStore} should be
	 *                        notified about this update.
	 * @param eventTime the time (in the time zone of the user) of the event that triggered the
	 *                  removal of this variable
	 * @param source the source of the update to this {@link VariableStore}.
	 * @return the {@link Variable} that was removed, or {@code null}.
	 */
	public Variable removeByName(String name, boolean notifyObservers,
                                 ZonedDateTime eventTime,
                                 VariableStoreChange.Source source) {
		Variable result;
		synchronized (variables) {
			result = variables.remove(name);
		}
		if(result == null) {
			return null;
		} else {
			if(notifyObservers) {
				notifyOnChange(new VariableStoreChange.Remove(name, eventTime, source));
			}
			return result;
		}
	}

	/**
	 * Adds all the entries in the {@code variablesToAdd}-map as {@link Variable}s to this
	 * {@link VariableStore}. The {@code variablesToAdd}-map is treated as a mapping from
	 * variable names ({@link String}s) to variable values ({@link Object}s).
	 * @param variablesToAdd the {@link Map} of name-value pairs to add as {@link Variable}s.
	 * @param notifyObservers true if observers of this {@link VariableStore} should be
	 *                        notified about this update.
	 * @param eventTime the time of the event that triggered the addition of these variables
	 *                  (in the time zone of the user)
	 */
	public void addAll(Map<? extends String, ?> variablesToAdd, boolean notifyObservers,
					   ZonedDateTime eventTime) {
		addAll(variablesToAdd,notifyObservers,eventTime, VariableStoreChange.Source.UNKNOWN);
	}

	/**
	 * Adds all the entries in the {@code variablesToAdd}-map as {@link Variable}s to this
	 * {@link VariableStore}. The {@code variablesToAdd}-map is treated as a mapping from
	 * variable names ({@link String}s) to variable values ({@link Object}s).
	 * @param variablesToAdd the {@link Map} of name-value pairs to add as {@link Variable}s.
	 * @param notifyObservers true if observers of this {@link VariableStore} should be
	 *                        notified about this update.
	 * @param eventTime the time of the event that triggered the addition of these variables
	 *                  (in the time zone of the user)
	 * @param source the source of the update to this {@link VariableStore}.
	 */
	public void addAll(Map<? extends String, ?> variablesToAdd, boolean notifyObservers,
					   ZonedDateTime eventTime, VariableStoreChange.Source source) {
		List<Variable> VariablesToAdd = new ArrayList<>();

		for (Map.Entry<? extends String, ?> entry : variablesToAdd.entrySet()) {
			String name = entry.getKey();
			Object value = entry.getValue();
			Variable Variable = new Variable(name,value,eventTime);
			VariablesToAdd.add(Variable);
		}

		synchronized (variables) {
			for(Variable Variable : VariablesToAdd) {
				variables.put(Variable.getName(), Variable);
			}
		}

		if (notifyObservers) {
			notifyOnChange(new VariableStoreChange.Put(VariablesToAdd, eventTime, source));
		}
	}

	/**
	 * Sets the {@link User} for this {@link VariableStore}.
	 * @param user the {@link User} for this {@link VariableStore}.
	 */
	public void setUser(User user) {
		this.user = user;
	}

	/**
	 * Returns a modifiable mapping of {@link String}s to {@link Object}s that is linked to the
	 * contents of this {@link VariableStore}. The {@link Object} values in this map are the
	 * values of the stored {@link Variable}s, so not the {@link Variable}s themselves!
	 * This {@code Map<String,Object>} can be used as a regular map, but is actually a specific
	 * implementation for this variable store. All basic map operations on the resulting map are
	 * observable by the {@link VariableStoreOnChangeListener}s that are registered to listen to
	 * this {@link VariableStore}.
	 *
	 * <p>This "modifiable map" is used in the execution of Dialogue Branch Dialogues containing
	 * Variables, as the implementation relies on the
	 * {@link nl.rrd.utils.expressions.Expression} interface.</p>
	 *
	 * <p>In other words, if you are thinking "Man, I wish VariableStore was just a simple
	 * mapping of variable names to values", use this method, and you can pretend that that is the
	 * case.</p>
	 *
	 * If {@code notifyObservers} is {@code true}, then any action that modifies the content of this
	 * {@link Map} will result in all listeners being notified.
	 *
	 * @param notifyObservers true if observers of this {@link VariableStore} should be notified
	 *                        about updates to the Map.
	 * @param eventTime the time of the event that is causing the changes to this
	 *                  {@link VariableStore} in the time zone of the user.
	 * @return the modifiable map
	 */
	public Map<String, Object> getModifiableMap(boolean notifyObservers, ZonedDateTime eventTime) {
		return new VariableMap(notifyObservers, eventTime,
				VariableStoreChange.Source.UNKNOWN);
	}

	/**
	 * See {@link #getModifiableMap(boolean, ZonedDateTime)}.
	 * @param notifyObservers true if observers of this {@link VariableStore} should be notified
	 *                        about updates to the Map.
	 * @param eventTime the time of the event that is causing the changes to this
	 * 	                {@link VariableStore} in the time zone of the user.
	 * @param source the source of the changes to this {@link VariableStore}.
	 * @return the modifiable map
	 */
	public Map<String, Object> getModifiableMap(boolean notifyObservers, ZonedDateTime eventTime,
												VariableStoreChange.Source source) {
		return new VariableMap(notifyObservers, eventTime, source);
	}

	/**
	 * A {@link VariableMap} is a mapping from Variable name to Variable value and can be used as an
	 * observable and modifiable "view" of the {@link VariableStore} whose changes are maintained
	 * within this encapsulating {@link VariableStore} object.
	 */
	private class VariableMap implements Map<String, Object> {

		private final boolean notifyObservers;
		private final ZonedDateTime eventTime;
		private final VariableStoreChange.Source source;

		// --------------------------------------------------------
		// -------------------- Constructor(s) --------------------
		// --------------------------------------------------------

		/**
		 * Creates an instance of a {@link VariableMap} which is a mapping of Strings to Objects
		 * representing the contents of this {@link VariableStore}.
		 * @param notifyObservers whether {@link VariableStoreOnChangeListener}s should be
		 *                        notified of updates made to this {@link VariableMap}.
		 * @param eventTime the timestamp that is passed along to all changes that are made in this
		 *                  {@link VariableMap}.
		 */
		public VariableMap(boolean notifyObservers, ZonedDateTime eventTime,
						   VariableStoreChange.Source source) {
			this.notifyObservers = notifyObservers;
			this.eventTime = eventTime;
			this.source = source;
		}

		// --------------------------------------------------------------
		// -------------------- Modification Methods --------------------
		// --------------------------------------------------------------

		@Override
		public Object put(String key, Object value) {
			Object result = get(key);
			setValue(key, value, notifyObservers, eventTime, source);
			return result;
		}

		@Override
		public Object remove(Object key) {
			Variable result = removeByName((String)key, notifyObservers, eventTime, source);
			if(result != null) return result.getValue();
			else return null;
		}

		@Override
		public void putAll(Map<? extends String, ?> variablesToAdd) {
			addAll(variablesToAdd,notifyObservers,eventTime, source);
		}

		@Override
		public void clear() {
			synchronized (variables) {
				variables.clear();
			}
			if (notifyObservers)
				notifyOnChange(new VariableStoreChange.Clear(eventTime, source));
		}

		// -----------------------------------------------------------
		// -------------------- Retrieval Methods --------------------
		// -----------------------------------------------------------

		@Override
		public int size() {
			synchronized (variables) {
				return variables.size();
			}
		}

		@Override
		public boolean isEmpty() {
			synchronized (variables) {
				return variables.isEmpty();
			}
		}

		@Override
		public boolean containsKey(Object key) {
			synchronized (variables) {
				return variables.containsKey(key);
			}
		}

		@Override
		public Object get(Object key) {
			synchronized (variables) {
				if(variables.get(key) != null) {
					return variables.get(key).getValue();
				} else return null;
			}
		}

		@Override
		public boolean containsValue(Object value) {
			synchronized (variables) {
				for (Map.Entry<String, Variable> entry : variables.entrySet()) {
					if(entry.getValue().getValue().equals(value)) return true;
				}
				return false;
			}
		}

		@Override
		public Set<String> keySet() {
			synchronized (variables) {
				return variables.keySet();
			}
		}

		@Override
		public Collection<Object> values() {
			Collection<Object> objectCollection = new ArrayList<>();

			synchronized (variables) {
				Collection<Variable> VariableCollection = variables.values();
				for(Variable Variable : VariableCollection) {
					objectCollection.add(Variable.getValue());
				}
			}

			return objectCollection;
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			Set<Entry<String,Object>> resultSet = new HashSet<>();

			synchronized (variables) {
				Set<Entry<String, Variable>> entrySet = variables.entrySet();

				for(Entry<String, Variable> entry : entrySet) {
					String key = entry.getKey();
					Object value = entry.getValue().getValue();
					Map.Entry<String,Object> newEntry = new AbstractMap.SimpleEntry<>(key, value);
					resultSet.add(newEntry);
				}
			}

			return resultSet;
		}
	}

}
