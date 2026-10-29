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

package com.dialoguebranch.web.service.storage;

import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.web.service.DlbProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalVariableServiceUpdaterTest {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

	private FakeExternalVariableService service;

	@AfterEach
	void stopService() {
		if (service != null) service.stop();
	}

	private static VariableStore storeNotifying(FakeExternalVariableService service) {
		DlbProperties properties = new DlbProperties();
		DlbProperties.ExternalVariableService evs = properties.getExternalVariableService();
		service.configure(evs);
		evs.setConnectTimeout(Duration.ofMillis(500));
		evs.setReadTimeout(Duration.ofMillis(500));
		VariableStore store = new VariableStore(new User("sub", ZONE));
		store.addOnChangeListener(new ExternalVariableServiceUpdater(
				new ExternalVariableServiceClient(properties), "iss", "sub", "p"));
		return store;
	}

	@Test
	void aChangeFromADialogueIsSentToTheService() throws IOException {
		service = FakeExternalVariableService.answering(200, "", Duration.ZERO);
		VariableStore store = storeNotifying(service);

		store.setValue("mood", "good", true, ZonedDateTime.now(ZONE),
				VariableUpdatedSource.DLB_SCRIPT);

		assertEquals(1, service.requests().size());
		assertEquals("/v1/variables/notify-updated", service.requests().get(0).path());
		assertTrue(service.requests().get(0).body().contains("\"mood\""));
	}

	@Test
	void aChangeFromTheServiceItselfIsNotSentBack() throws IOException {
		service = FakeExternalVariableService.answering(200, "", Duration.ZERO);
		VariableStore store = storeNotifying(service);

		store.setValue("steps", 42, true, ZonedDateTime.now(ZONE),
				VariableUpdatedSource.EXTERNAL);

		assertEquals(0, service.requests().size());
	}

	@Test
	void aFailingOrSlowServiceNeverBreaksTheDialogueTurn() throws IOException {
		service = FakeExternalVariableService.answering(500, "", Duration.ofSeconds(2));
		VariableStore store = storeNotifying(service);

		assertDoesNotThrow(() -> store.setValue("mood", "good", true, ZonedDateTime.now(ZONE),
				VariableUpdatedSource.DLB_SCRIPT));
		assertEquals("good", store.getValue("mood"));
	}
}
