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

import com.dialoguebranch.model.FileDescriptor;
import com.dialoguebranch.model.ResourceType;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.ProtocolVersion;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import com.dialoguebranch.web.service.controller.schema.DialogueListPayload;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.rrd.utils.AppComponents;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the /admin/... end-points of the Dialogue Branch Web Service.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
@RestController
@SecurityRequirement(name = "X-Auth-Token")
@RequestMapping(value = {"/v{version}/admin", "/admin"})
@Tag(name = "6. Admin", description = "End-points for administrative control of the Dialogue " +
    "Branch Web Service.")
public class AdminController {

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
    public AdminController() { }

    // ---------------------------------------------------------------------------- //
    // -------------------- END-POINT: "/admin/list-dialogues" -------------------- //
    // ---------------------------------------------------------------------------- //

    /**
     * Retrieve a list of all available dialogues in the Web Service.
     *
     * <p>This method returns a JSON object encapsulating a list of all dialogue names that are
     * hosted by the running instance of this Dialogue Branch Web Service.</p>
     *
     * @param request the HTTPRequest object (to retrieve authentication headers and optional body
     *                parameters).
     * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
     *                 Unauthorized error).
     * @param version The API Version to use, e.g. '1'.
     * @return a {@link DialogueListPayload} object containing a list of all available dialogues
     *         in the Web Service.
     * @throws UnauthorizedException in case the logged-in user does not have admin rights.
     */
    @Operation(
        summary = "Retrieve a list of all available dialogues in the Web Service.",
        description = "This method returns a JSON object encapsulating a list of all dialogue " +
            "names that are hosted by the running instance of this Dialogue Branch Web Service. " +
            "Only accessible for users with the 'admin' role.")
    @RequestMapping(value="/list-dialogues", method=RequestMethod.GET)
    public DialogueListPayload listDialogues(
        HttpServletRequest request,
        HttpServletResponse response,

        @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
        @PathVariable(value = "version")
        String version
    ) throws UnauthorizedException {

        // If no versionName is provided, or versionName is empty, assume the latest version
        if (version == null || version.isEmpty()) {
            version = ProtocolVersion.getLatestVersion().versionName();
        }

        // Log this call to the service log
        String logInfo = "GET /v" + version + "/admin/list-dialogues";
        logger.info(logInfo);

        AuthenticationInfo authenticationInfo = QueryRunner.validateAccessToken(
                ControllerFunctions.extractAccessToken(request),application);
        if(authenticationInfo.hasRole(BasicUserCredentials.USER_ROLE_ADMIN)) {
            return doListDialogues();
        } else {
            throw new UnauthorizedException(ErrorCode.INSUFFICIENT_PRIVILEGES,
                "This user does not have the rights to access this function.");
        }
    }

    /**
     * Processes a call to the /admin/list-dialogues end-point. Constructs and returns a {@link
     * DialogueListPayload} object, encapsulating the names of all dialogue scripts currently loaded
     * by this Dialogue Branch Web Service.
     *
     * @return a {@link DialogueListPayload} containing a list of all dialogue names.
     */
    private DialogueListPayload doListDialogues() {
        List<FileDescriptor> files = application.getApplicationManager().getAvailableDialogues();

        List<String> scriptNames = new ArrayList<>();

        for(FileDescriptor fileDescriptor : files) {
            if(fileDescriptor.getFileType().equals(ResourceType.SCRIPT)) {
                scriptNames.add(fileDescriptor.getDialogueName());
            }
        }

        return new DialogueListPayload(scriptNames.toArray(new String[0]));
    }

}
