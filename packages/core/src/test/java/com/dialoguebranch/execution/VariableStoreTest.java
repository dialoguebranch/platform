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

package com.dialoguebranch.execution;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the {@link VariableStore} runtime and its supporting types — {@link Variable},
 * {@link User}, the {@link VariableStoreChange} hierarchy, and the observable {@code
 * getModifiableMap} view. Part of the #90 area-A test hardening (#154).
 */
public class VariableStoreTest {

	private static final ZonedDateTime T =
			ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneId.of("Europe/Lisbon"));

	private VariableStore newStore() {
		return new VariableStore(new User("tester"));
	}

	/** Collects every {@link VariableStoreChange} the store dispatches. */
	private static class RecordingListener implements VariableStoreOnChangeListener {
		final List<VariableStoreChange> changes = new ArrayList<>();

		@Override
		public void onChange(VariableStore store, List<VariableStoreChange> changes) {
			this.changes.addAll(changes);
		}
	}

	// ---------------------------------------------------------------- //
	// -------------------- VariableStore: basics --------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void setThenGet() {
		VariableStore store = newStore();
		store.setValue("name", "Alice", false, T);

		assertEquals("Alice", store.getValue("name"));
		assertEquals("Alice", store.getVariable("name").getValue());
		assertEquals(VariableUpdatedSource.UNKNOWN, store.getVariable("name").getUpdatedSource());
	}

	@Test
	public void getMissingVariableReturnsNull() {
		VariableStore store = newStore();
		assertNull(store.getVariable("nope"));
		assertNull(store.getValue("nope"));
	}

	@Test
	public void setValueRecordsTheGivenSource() {
		VariableStore store = newStore();
		store.setValue("x", 1, false, T, VariableUpdatedSource.WEB_SERVICE);
		assertEquals(VariableUpdatedSource.WEB_SERVICE, store.getVariable("x").getUpdatedSource());
	}

	@Test
	public void overwritingAVariableReplacesItsValue() {
		VariableStore store = newStore();
		store.setValue("x", 1, false, T);
		store.setValue("x", 2, false, T);
		assertEquals(2, store.getValue("x"));
		assertEquals(1, store.getVariables().length);
	}

	@Test
	public void nameCollectionsReflectContentsAndAreSorted() {
		VariableStore store = newStore();
		store.setValue("charlie", 1, false, T);
		store.setValue("alpha", 2, false, T);
		store.setValue("bravo", 3, false, T);

		assertEquals(3, store.getVariableNames().size());
		assertTrue(store.getVariableNames().contains("bravo"));
		assertEquals(List.of("alpha", "bravo", "charlie"), store.getSortedVariableNames());
	}

	@Test
	public void arrayConstructorPrePopulatesTheStore() {
		Variable[] seed = {
				new Variable("a", "one", T, VariableUpdatedSource.DLB_SCRIPT),
				new Variable("b", "two", T, VariableUpdatedSource.DLB_SCRIPT),
		};
		VariableStore store = new VariableStore(new User("u"), seed);
		assertEquals("one", store.getValue("a"));
		assertEquals("two", store.getValue("b"));
	}

	@Test
	public void userIsReadableAndReplaceable() {
		VariableStore store = newStore();
		assertEquals("tester", store.getUser().getId());
		User other = new User("other");
		store.setUser(other);
		assertSame(other, store.getUser());
	}

	@Test
	public void removeByNameReturnsTheRemovedVariableOrNull() {
		VariableStore store = newStore();
		store.setValue("x", 42, false, T);

		Variable removed = store.removeByName("x", false, T);
		assertEquals(42, removed.getValue());
		assertNull(store.getVariable("x"));
		assertNull(store.removeByName("x", false, T));
	}

	@Test
	public void addAllInsertsEveryEntry() {
		VariableStore store = newStore();
		Map<String, Object> batch = new LinkedHashMap<>();
		batch.put("a", 1);
		batch.put("b", "two");
		store.addAll(batch, false, T, VariableUpdatedSource.EXTERNAL);

		assertEquals(1, store.getValue("a"));
		assertEquals("two", store.getValue("b"));
		assertEquals(VariableUpdatedSource.EXTERNAL, store.getVariable("a").getUpdatedSource());
	}

	// ---------------------------------------------------------------- //
	// -------------------- VariableStore: listeners ----------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void setValueNotifiesWithAPutChangeCarryingTimeAndSource() {
		VariableStore store = newStore();
		RecordingListener listener = new RecordingListener();
		store.addOnChangeListener(listener);

		store.setValue("x", 7, true, T, VariableUpdatedSource.DLB_SCRIPT);

		assertEquals(1, listener.changes.size());
		VariableStoreChange change = listener.changes.get(0);
		assertTrue(change instanceof VariableStoreChange.Put);
		assertEquals(T, change.getTime());
		assertEquals(VariableUpdatedSource.DLB_SCRIPT, change.getSource());
		assertEquals(7, ((VariableStoreChange.Put) change).getVariables().get("x"));
	}

	@Test
	public void notifyFlagFalseSuppressesTheEvent() {
		VariableStore store = newStore();
		RecordingListener listener = new RecordingListener();
		store.addOnChangeListener(listener);

		store.setValue("x", 1, false, T);
		store.removeByName("x", false, T);

		assertTrue(listener.changes.isEmpty());
	}

	@Test
	public void removeByNameNotifiesWithARemoveChange() {
		VariableStore store = newStore();
		store.setValue("x", 1, false, T);
		RecordingListener listener = new RecordingListener();
		store.addOnChangeListener(listener);

		store.removeByName("x", true, T, VariableUpdatedSource.WEB_SERVICE);

		assertEquals(1, listener.changes.size());
		VariableStoreChange.Remove change = (VariableStoreChange.Remove) listener.changes.get(0);
		assertTrue(change.getVariableNames().contains("x"));
		assertEquals(VariableUpdatedSource.WEB_SERVICE, change.getSource());
	}

	@Test
	public void removingAnAbsentVariableDoesNotNotify() {
		VariableStore store = newStore();
		RecordingListener listener = new RecordingListener();
		store.addOnChangeListener(listener);

		assertNull(store.removeByName("ghost", true, T));
		assertTrue(listener.changes.isEmpty());
	}

	@Test
	public void addAllNotifiesOnceForTheWholeBatch() {
		VariableStore store = newStore();
		RecordingListener listener = new RecordingListener();
		store.addOnChangeListener(listener);

		Map<String, Object> batch = new LinkedHashMap<>();
		batch.put("a", 1);
		batch.put("b", 2);
		store.addAll(batch, true, T);

		assertEquals(1, listener.changes.size());
		VariableStoreChange.Put put = (VariableStoreChange.Put) listener.changes.get(0);
		assertEquals(2, put.getVariables().size());
	}

	@Test
	public void everyRegisteredListenerIsNotifiedUntilRemoved() {
		VariableStore store = newStore();
		RecordingListener a = new RecordingListener();
		RecordingListener b = new RecordingListener();
		store.addOnChangeListener(a);
		store.addOnChangeListener(b);

		store.setValue("x", 1, true, T);
		assertEquals(1, a.changes.size());
		assertEquals(1, b.changes.size());

		assertTrue(store.removeOnChangeListener(b));
		assertFalse(store.removeOnChangeListener(b));

		store.setValue("y", 2, true, T);
		assertEquals(2, a.changes.size());
		assertEquals(1, b.changes.size());
	}

	// ---------------------------------------------------------------- //
	// -------------------- getModifiableMap view -------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void modifiableMapWritesThroughToTheStoreAndReturnsThePreviousValue() {
		VariableStore store = newStore();
		Map<String, Object> map = store.getModifiableMap(false, T);

		assertNull(map.put("x", 1));
		assertEquals(1, map.put("x", 2));
		assertEquals(2, store.getValue("x"));
	}

	@Test
	public void modifiableMapPutAndRemoveNotifyWithMatchingSource() {
		VariableStore store = newStore();
		RecordingListener listener = new RecordingListener();
		store.addOnChangeListener(listener);
		Map<String, Object> map =
				store.getModifiableMap(true, T, VariableUpdatedSource.DLB_SCRIPT);

		map.put("x", 1);
		Object previous = map.remove("x");

		assertEquals(1, previous);
		assertEquals(2, listener.changes.size());
		assertTrue(listener.changes.get(0) instanceof VariableStoreChange.Put);
		assertTrue(listener.changes.get(1) instanceof VariableStoreChange.Remove);
		assertEquals(VariableUpdatedSource.DLB_SCRIPT, listener.changes.get(1).getSource());
	}

	@Test
	public void modifiableMapRemoveWithNonStringKeyIsANoOp() {
		VariableStore store = newStore();
		Map<String, Object> map = store.getModifiableMap(false, T);
		assertNull(map.remove(42));
	}

	@Test
	public void modifiableMapPutAllDelegatesToAddAll() {
		VariableStore store = newStore();
		Map<String, Object> map = store.getModifiableMap(false, T);
		Map<String, Object> batch = new LinkedHashMap<>();
		batch.put("a", 1);
		batch.put("b", 2);
		map.putAll(batch);
		assertEquals(2, store.getVariables().length);
	}

	@Test
	public void modifiableMapClearEmptiesTheStoreAndNotifiesWithClear() {
		VariableStore store = newStore();
		store.setValue("a", 1, false, T);
		store.setValue("b", 2, false, T);
		RecordingListener listener = new RecordingListener();
		store.addOnChangeListener(listener);
		Map<String, Object> map = store.getModifiableMap(true, T);

		map.clear();

		assertTrue(map.isEmpty());
		assertEquals(0, store.getVariables().length);
		assertEquals(1, listener.changes.size());
		assertTrue(listener.changes.get(0) instanceof VariableStoreChange.Clear);
	}

	@Test
	public void modifiableMapReadAccessorsReflectTheStore() {
		VariableStore store = newStore();
		store.setValue("a", "x", false, T);
		store.setValue("b", "y", false, T);
		Map<String, Object> map = store.getModifiableMap(false, T);

		assertEquals(2, map.size());
		assertFalse(map.isEmpty());
		assertTrue(map.containsKey("a"));
		assertEquals("x", map.get("a"));
		assertNull(map.get("missing"));
		assertTrue(map.containsValue("y"));
		assertFalse(map.containsValue("z"));
		assertEquals(2, map.keySet().size());
		assertTrue(map.values().contains("x"));
		assertEquals(2, map.entrySet().size());
	}

	// ---------------------------------------------------------------- //
	// -------------------- Variable ------------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void variableFromZonedDateTimeSplitsIntoEpochAndZone() {
		Variable v = new Variable("v", "val", T, VariableUpdatedSource.DLB_SCRIPT);
		assertEquals("v", v.getName());
		assertEquals("val", v.getValue());
		assertEquals(Long.valueOf(T.toInstant().toEpochMilli()), v.getUpdatedTime());
		assertEquals("Europe/Lisbon", v.getUpdatedTimeZone());
		assertEquals(T.toInstant(), v.getZonedUpdatedTime().toInstant());
	}

	@Test
	public void variableRawConstructorDefaultsNullSourceToUnknown() {
		Variable v = new Variable("v", 1, 0L, "UTC", null);
		assertEquals(VariableUpdatedSource.UNKNOWN, v.getUpdatedSource());
	}

	@Test
	public void variableUnknownHasNameOnly() {
		Variable v = Variable.unknown("mystery");
		assertEquals("mystery", v.getName());
		assertNull(v.getValue());
		assertNull(v.getUpdatedTime());
		assertNull(v.getUpdatedTimeZone());
		// no zone + no timestamp => epoch 0 in the system default zone
		assertEquals(0L, v.getZonedUpdatedTime().toInstant().toEpochMilli());
	}

	@Test
	public void variableToStringMentionsNameAndValue() {
		String s = new Variable("age", 30, T, VariableUpdatedSource.WEB_SERVICE).toString();
		assertTrue(s.contains("age"));
		assertTrue(s.contains("30"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- User ----------------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void userDefaultsToTheSystemTimeZone() {
		User u = new User("u");
		assertEquals(ZoneId.systemDefault(), u.getTimeZone());
	}

	@Test
	public void userFieldsAreMutable() {
		User u = new User("u", ZoneId.of("America/New_York"));
		assertEquals("u", u.getId());
		assertEquals(ZoneId.of("America/New_York"), u.getTimeZone());
		u.setId("v");
		u.setTimeZone(ZoneId.of("Asia/Tokyo"));
		assertEquals("v", u.getId());
		assertEquals(ZoneId.of("Asia/Tokyo"), u.getTimeZone());
	}

	// ---------------------------------------------------------------- //
	// -------------------- VariableStoreChange -------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void putChangeFlattensEveryConstructorToNameValuePairs() {
		Variable v1 = new Variable("a", 1, T, VariableUpdatedSource.DLB_SCRIPT);
		Variable v2 = new Variable("b", 2, T, VariableUpdatedSource.DLB_SCRIPT);
		Map<String, Variable> varMap = new LinkedHashMap<>();
		varMap.put("a", v1);
		varMap.put("b", v2);

		assertEquals(Map.of("a", 1, "b", 2),
				new VariableStoreChange.Put(varMap, T).getVariables());
		assertEquals(Map.of("a", 1, "b", 2),
				new VariableStoreChange.Put(varMap, T, VariableUpdatedSource.EXTERNAL).getVariables());
		assertEquals(Map.of("k", "v"),
				new VariableStoreChange.Put("k", "v", T).getVariables());
		assertEquals(Map.of("k", "v"),
				new VariableStoreChange.Put("k", "v", T, VariableUpdatedSource.EXTERNAL).getVariables());
		assertEquals(Map.of("a", 1),
				new VariableStoreChange.Put(v1, T).getVariables());
		assertEquals(Map.of("a", 1),
				new VariableStoreChange.Put(v1, T, VariableUpdatedSource.EXTERNAL).getVariables());
		assertEquals(Map.of("a", 1, "b", 2),
				new VariableStoreChange.Put(List.of(v1, v2), T).getVariables());
		assertEquals(Map.of("a", 1, "b", 2),
				new VariableStoreChange.Put(List.of(v1, v2), T, VariableUpdatedSource.EXTERNAL)
						.getVariables());
	}

	@Test
	public void putChangeExposesTimeAndSource() {
		VariableStoreChange.Put change =
				new VariableStoreChange.Put("k", "v", T, VariableUpdatedSource.WEB_SERVICE);
		assertEquals(T, change.getTime());
		assertEquals(VariableUpdatedSource.WEB_SERVICE, change.getSource());
		// the no-source constructors default to UNKNOWN
		assertEquals(VariableUpdatedSource.UNKNOWN,
				new VariableStoreChange.Put("k", "v", T).getSource());
	}

	@Test
	public void removeChangeCarriesTheRemovedNames() {
		assertEquals(List.of("x"),
				new ArrayList<>(new VariableStoreChange.Remove("x", T).getVariableNames()));
		assertEquals(List.of("x"),
				new ArrayList<>(new VariableStoreChange.Remove("x", T, VariableUpdatedSource.EXTERNAL)
						.getVariableNames()));

		List<String> names = List.of("a", "b");
		assertEquals(names, new ArrayList<>(
				new VariableStoreChange.Remove(names, T).getVariableNames()));
		VariableStoreChange.Remove withSource =
				new VariableStoreChange.Remove(names, T, VariableUpdatedSource.WEB_SERVICE);
		assertEquals(names, new ArrayList<>(withSource.getVariableNames()));
		assertEquals(VariableUpdatedSource.WEB_SERVICE, withSource.getSource());
	}

	@Test
	public void clearChangeCarriesTimeAndSource() {
		assertEquals(VariableUpdatedSource.UNKNOWN, new VariableStoreChange.Clear(T).getSource());
		VariableStoreChange.Clear withSource =
				new VariableStoreChange.Clear(T, VariableUpdatedSource.DLB_SCRIPT);
		assertEquals(T, withSource.getTime());
		assertEquals(VariableUpdatedSource.DLB_SCRIPT, withSource.getSource());
	}
}
