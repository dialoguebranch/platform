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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.model.common.Speaker;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeHeader;
import com.dialoguebranch.model.execute.ResourcePointer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Reports a warning for every {@link Node} whose speaker isn't one of the project's speakers — used
 * by {@link ProjectParser}, split out as one of its independent validation passes.
 *
 * <p>A project that defines no speakers gets no such warnings: defining speakers is optional, and
 * without them any speaker name is fine. A node with an undefined speaker still parses and runs as
 * before; it is usually a typo or a speaker that was never added to the project, so it is reported
 * as a warning rather than a parse error.</p>
 *
 * @author Harm op den Akker
 */
class UndefinedSpeakerValidator {

	/** Utility class — no instances. */
	private UndefinedSpeakerValidator() {
	}

	/**
	 * @param allParsedDialogues every dialogue that parsed at all, keyed by its source file.
	 * @param speakers the project's speakers; when empty, nothing is reported.
	 * @param onWarning called with the originating file and a warning message for each node whose
	 *                  speaker isn't one of {@code speakers}.
	 */
	static void detect(Map<ResourcePointer, Dialogue> allParsedDialogues, List<Speaker> speakers,
					   BiConsumer<ResourcePointer, String> onWarning) {
		if (speakers.isEmpty())
			return;
		Set<String> names = speakers.stream().map(Speaker::name).collect(Collectors.toSet());
		for (Map.Entry<ResourcePointer, Dialogue> entry : allParsedDialogues.entrySet()) {
			for (Node node : entry.getValue().getNodes()) {
				NodeHeader header = node.getHeader();
				String speaker = header == null ? null : header.getSpeaker();
				if (speaker != null && !names.contains(speaker)) {
					onWarning.accept(entry.getKey(), String.format(
							"Node \"%s\" has speaker \"%s\", which isn't one of the project's " +
							"speakers", node.getTitle(), speaker));
				}
			}
		}
	}
}
