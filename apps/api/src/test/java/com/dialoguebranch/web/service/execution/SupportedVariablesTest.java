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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.InternalServerErrorException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ApplicationManager#getSupportedVariablesFromExternalService} backs the
 * {@code /variables/list-supported} end-point (dialoguebranch/platform#185). The test profile has
 * no External Variable Service configured, which is exactly the "not enabled" path this covers;
 * the live-proxy success path needs a mock EVS HTTP server, which is out of scope here since no
 * such test infrastructure exists yet for any of the EVS-calling code in this module.
 */
@SpringBootTest
@ActiveProfiles("test")
class SupportedVariablesTest {

	@Autowired
	private Application application;

	@Test
	void throwsWithTheExpectedErrorCodeWhenNoExternalVariableServiceIsConfigured() {
		InternalServerErrorException exception = assertThrows(InternalServerErrorException.class,
				() -> application.getApplicationManager()
						.getSupportedVariablesFromExternalService("default-test"));

		assertEquals(ErrorCode.EXTERNAL_VARIABLE_SERVICE_NOT_ENABLED,
				exception.getError().getCode());
	}
}
