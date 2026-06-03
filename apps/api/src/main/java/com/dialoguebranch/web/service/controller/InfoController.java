/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.controller.schema.ServiceInfoPayload;
import nl.rrd.utils.AppComponents;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.ProtocolVersion;
import com.dialoguebranch.web.service.ServiceContext;
import com.dialoguebranch.web.service.Configuration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Controller for the /info/... end-points of the Dialogue Branch Web Service.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
@RestController
@Tag(name = "5. Information", description = "End-points that provide information about the " +
		"running service.")
@RequestMapping(value = {"/v{version}/info", "/info"})
public class InfoController {

	@Autowired
	Application application;

	/** Used for writing logging information */
	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Instances of this class are constructed through Spring.
	 */
	public InfoController() { }

	// ---------------------------------------------------------------- //
	// -------------------- END-POINT: "/info/all" -------------------- //
	// ---------------------------------------------------------------- //

	/**
	 * Retrieve a set of metadata parameters about the running service.
	 *
	 * <p>This end-point may be called without authentication and will return 4 variables that
	 * describe the current version of the service:
	 * <ul>
	 * 		<li>build - Date &amp; Time when the service was built</li>
	 * 		<li>protocolVersion - latest supported API Protocol version</li>
	 * 		<li>serviceVersion - software version of the service</li>
	 * 		<li>upTime - string showing days, hours and minutes since the service was launched</li>
	 * </ul>
	 * </p>
	 *
	 * @param version The API version to use, e.g. '1'.
	 * @return a {@link ServiceInfoPayload} object containing information about the running service.
	 */
	@Operation(summary = "Retrieve a set of metadata parameters about the running service.",
		description = "This end-point may be called without authentication and will return 4 " +
			"variables that describe the current version of the service:" +
			" <ul><li>build - Date & Time when the service was built</li>" +
			" <li>protocolVersion - latest supported API Protocol version</li>" +
			" <li>serviceVersion - software version of the service</li>" +
			" <li>upTime - string showing days, hours and minutes since the service was launched " +
			"</li></ul>")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/all")
	public ServiceInfoPayload all(
			@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
			@PathVariable(value = "version")
			String version
	) {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
        logger.info("GET /v{}/info/all", version);

		// Construct the string that indicates the service's uptime
		Long launchedTime = application.getLaunchedTime();
		Long currentTime = Instant.now().toEpochMilli();
		long upTimeMillis = currentTime - launchedTime;
		long days = TimeUnit.MILLISECONDS.toDays(upTimeMillis);
		upTimeMillis = upTimeMillis - (days * 86400000);
		long hours = TimeUnit.MILLISECONDS.toHours(upTimeMillis);
		upTimeMillis = upTimeMillis - (hours * 3600000);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(upTimeMillis);
		String upTimeString = days + "d " + hours + "h " + minutes + "m";

		return new ServiceInfoPayload(
				Configuration.getInstance().get(Configuration.BUILD_TIME),
				ServiceContext.getCurrentVersion(),
				Configuration.getInstance().get(Configuration.VERSION),
				upTimeString);
	}

}
