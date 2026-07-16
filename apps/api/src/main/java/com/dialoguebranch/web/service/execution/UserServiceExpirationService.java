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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.DlbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Periodically evicts {@link UserService}s that have been idle for longer than {@link
 * DlbProperties.Session#getIdleTimeoutMinutes()}.
 *
 * <p>A {@link UserService} is otherwise only ever removed by an explicit {@code /auth/logout}
 * call (see {@code AuthController#doLogout}), so without this sweep, clients that disconnect
 * without logging out — a closed browser tab, a killed app, an expired token, a dropped
 * connection — would leave their {@link UserService} (and its {@link VariableStore}, {@link
 * com.dialoguebranch.web.service.storage.LoggedDialogueStore}) in memory for the remaining
 * lifetime of the server process.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class UserServiceExpirationService {

	private static final Logger logger =
			LoggerFactory.getLogger(UserServiceExpirationService.class);

	/** How often the idle-eviction sweep runs, regardless of the configured idle timeout. */
	private static final long SWEEP_INTERVAL_MILLIS = 5 * 60 * 1000;

	private final Application application;

	/**
	 * Creates a new {@link UserServiceExpirationService}.
	 *
	 * @param application the application instance whose {@link ApplicationManager} is swept for
	 *                    idle {@link UserService}s.
	 */
	public UserServiceExpirationService(Application application) {
		this.application = application;
	}

	/**
	 * Evicts every {@link UserService} that has been idle for longer than the configured timeout.
	 * Runs on a fixed interval (see {@link #SWEEP_INTERVAL_MILLIS}), independent of how the
	 * timeout itself is configured.
	 */
	@Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
	public void evictIdleUserServices() {
		DlbProperties dlbProperties = application.getDlbProperties();
		int idleTimeoutMinutes = dlbProperties.getSession().getIdleTimeoutMinutes();
		long idleTimeoutMillis = TimeUnit.MINUTES.toMillis(idleTimeoutMinutes);

		int evicted = application.getApplicationManager()
				.removeIdleUserServices(idleTimeoutMillis);

		if (evicted > 0) {
			logger.info("Evicted {} idle UserService instance(s) (idle timeout: {} minute(s)).",
					evicted, idleTimeoutMinutes);
		}
	}

}
