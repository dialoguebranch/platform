/* @license
 *
 *                Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

// Matches DialogueBranchParser.NODE_NAME_REGEX in packages/core.
const NODE_NAME_REGEX = /^[A-Za-z0-9_-]+$/;

const REPLY_PATTERN = /\[\[(.*?)]]/g;

/**
 * Extracts every `[[...]]` reply link from a raw `.dlb` node body — deliberately scoped to just
 * this one piece of syntax (not a full grammar parser) since it's all that's needed to draw graph
 * edges in the dialogue editor. Mirrors the reply-target conventions in `ReplyParser.java`:
 *
 * - `[[Target]]` — a single segment: a bare, unconditional ("auto-forward") link.
 * - `[[Reply text|Target]]` — two segments: display text + target.
 * - `[[Reply text|Target|<<command>>]]` — three segments: display text, target, and a command.
 *
 * A target matching `NODE_NAME_REGEX` (letters/digits/underscore/hyphen only) is an *internal*
 * link (same dialogue); anything else is treated as *external* (a `<dialogueRef>.<nodeTitle>`
 * cross-dialogue reference) — this mirrors the parser's own distinction, though resolving the
 * relative dialogue path itself (as `ExternalNodePointer` does) is left to the backend.
 *
 * @param {string} bodySource the raw body script to scan.
 * @returns {Array<{raw: string, displayText: string|null, target: string, actionRaw: string|null,
 *   isExternal: boolean, dialogueRef: string|null, nodeTitle: string}>} every reply link found, in
 *   the order they appear.
 */
export function extractReplyLinks(bodySource) {
    if (!bodySource) return [];
    const links = [];
    for (const match of bodySource.matchAll(REPLY_PATTERN)) {
        const inner = match[1];
        const parts = inner.split('|');
        const targetToken = (parts.length === 1 ? parts[0] : parts[1]).trim();
        if (!targetToken) continue;

        const displayText = parts.length === 1 ? null : parts[0];
        const actionRaw = parts.length === 3 ? parts[2] : null;
        const isExternal = !NODE_NAME_REGEX.test(targetToken);

        let dialogueRef = null;
        let nodeTitle = targetToken;
        if (isExternal) {
            const sep = targetToken.lastIndexOf('.');
            if (sep >= 0) {
                dialogueRef = targetToken.slice(0, sep);
                nodeTitle = targetToken.slice(sep + 1);
            }
        }

        links.push({
            raw: match[0],
            displayText,
            target: targetToken,
            actionRaw,
            isExternal,
            dialogueRef,
            nodeTitle,
        });
    }
    return links;
}
