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

import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Reports a warning for every {@link Node} that no reply link (internal or external) points to
 * and that is not its own {@link Dialogue}'s Start node — used by {@link ProjectParser}, split out
 * as one of its independent validation passes.
 *
 * <p>A Start node is always treated as reachable in its own right, since it is a valid standalone
 * entry point (e.g. via the Web Service's {@code dialogue/start} end-point) regardless of whether
 * anything within the project links to it. An external node pointer into another dialogue marks
 * its target node reachable, since the project parser has no way to know whether an external
 * caller will address it.</p>
 *
 * <p>Detecting orphaned nodes never affects parsing success or dialogue execution — a node that
 * nothing points to can simply never be reached, which is not an error by design (a dialogue is
 * not required to link every node it defines). It usually does indicate an authoring mistake
 * though (a branch left disconnected while editing), so it is reported as a warning rather than a
 * parse error.</p>
 *
 * @author Harm op den Akker
 */
class OrphanedNodeValidator {

	/** Utility class — no instances. */
	private OrphanedNodeValidator() {
	}

	/**
	 * @param allParsedDialogues every dialogue that parsed at all, keyed by its source file.
	 * @param dialoguesByName every successfully-parsed dialogue, keyed by dialogue name (see
	 *                        {@link DuplicateDialogueNameValidator}).
	 * @param onWarning called with the originating file and a warning message for each orphaned
	 *                  node found.
	 */
	static void detect(Map<ResourcePointer, Dialogue> allParsedDialogues,
					   Map<String, Dialogue> dialoguesByName,
					   BiConsumer<ResourcePointer, String> onWarning) {
		Map<Dialogue, Set<String>> reachableNodeIds = new HashMap<>();

		for (Dialogue dlg : allParsedDialogues.values()) {
			Set<String> reachable = reachableNodeIds.computeIfAbsent(dlg, (d) -> new HashSet<>());
			Node startNode = dlg.getStartNode();
			if (startNode != null)
				reachable.add(Objects.requireNonNull(startNode.getTitle()).toLowerCase());
			for (Node node : dlg.getNodes()) {
				for (NodePointer pointer
						: Objects.requireNonNull(node.getBody()).getNodePointers()) {
					if (pointer instanceof InternalNodePointer)
						reachable.add(pointer.getTargetNodeId().toLowerCase());
				}
			}
		}

		// External pointers can target nodes in OTHER dialogues, so fold those in across the
		// whole project after the per-dialogue internal pass above.
		for (Dialogue dlg : allParsedDialogues.values()) {
			for (ExternalNodePointer pointer : dlg.getExternalNodePointers()) {
				Dialogue targetDlg = dialoguesByName.get(pointer.getAbsoluteTargetDialogue());
				if (targetDlg == null)
					continue; // unknown target dialogue — already reported as a parse error
				reachableNodeIds.computeIfAbsent(targetDlg, (d) -> new HashSet<>())
						.add(pointer.getTargetNodeId().toLowerCase());
			}
		}

		for (Map.Entry<ResourcePointer, Dialogue> entry : allParsedDialogues.entrySet()) {
			Dialogue dlg = entry.getValue();
			Set<String> reachable = reachableNodeIds.getOrDefault(dlg, Set.of());
			for (Node node : dlg.getNodes()) {
				String nodeTitle = Objects.requireNonNull(node.getTitle());
				if (!reachable.contains(nodeTitle.toLowerCase())) {
					onWarning.accept(entry.getKey(), String.format(
							"Node \"%s\" is orphaned: no reply link points to it, and it is " +
							"not this dialogue's Start node", nodeTitle));
				}
			}
		}
	}
}
