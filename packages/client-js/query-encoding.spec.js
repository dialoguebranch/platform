/* @license
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

import { describe, expect, it, vi } from "vitest";
import { DialogueBranchClient } from "./DialogueBranchClient.js";
import { DialogueBranchAuthoringClient } from "./DialogueBranchAuthoringClient.js";

function captureUrl(client, call) {
    const fetchMock = vi.fn(() => new Promise(() => {}));
    client._fetch = fetchMock;
    call(client);
    return fetchMock.mock.calls[0][0];
}

describe("query parameter encoding", () => {
    it("encodes playback string parameters", () => {
        const client = new DialogueBranchClient({ baseUrl: "/api/v1" });

        expect(captureUrl(client, c => c.startDialogue({ dialogueName: "Q&A", projectSlug: "project", language: "en&debug=true" })))
            .toContain("dialogueName=Q%26A&projectSlug=project&language=en%26debug%3Dtrue");
        expect(captureUrl(client, c => c.progressDialogue("session&admin=true", 1, 2)))
            .toContain("loggedDialogueId=session%26admin%3Dtrue");
        expect(captureUrl(client, c => c.continueDialogue({ dialogueName: "Q&A", projectSlug: "project" })))
            .toContain("dialogueName=Q%26A");
        expect(captureUrl(client, c => c.cancelDialogue("session&admin=true")))
            .toContain("loggedDialogueId=session%26admin%3Dtrue");
        expect(captureUrl(client, c => c.setVariable({ variableName: "name&scope", variableValue: "one=1&two=2", projectSlug: "project" })))
            .toContain("name=name%26scope&value=one%3D1%26two%3D2");
    });

    it("encodes draft-session string parameters", () => {
        const client = new DialogueBranchAuthoringClient({ baseUrl: "/api/v1" });

        expect(captureUrl(client, c => c.startDraftDialogue({ projectSlug: "project", dialogueName: "draft", language: "en&debug=true" })))
            .toContain("language=en%26debug%3Dtrue");
        expect(captureUrl(client, c => c.progressDraftDialogue({ draftSessionId: "draft&admin=true", replyId: 1 })))
            .toContain("draftSessionId=draft%26admin%3Dtrue");
        expect(captureUrl(client, c => c.cancelDraftDialogue("draft&admin=true")))
            .toContain("draftSessionId=draft%26admin%3Dtrue");
        expect(captureUrl(client, c => c.revertDraftVariables({ draftSessionId: "draft&admin=true" })))
            .toContain("draftSessionId=draft%26admin%3Dtrue");
    });
});
