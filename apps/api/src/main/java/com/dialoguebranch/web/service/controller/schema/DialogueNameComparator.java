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

package com.dialoguebranch.web.service.controller.schema;

import java.util.Comparator;

/**
 * Orders {@code /}-separated dialogue names the way a file browser would: at every path level,
 * sub-folders are listed before loose dialogues, and entries are then ordered alphabetically
 * (case-insensitive). Names that differ only by case are given a stable order by a final
 * case-sensitive comparison.
 *
 * <p>Use the shared {@link #FOLDERS_FIRST} instance.</p>
 *
 * @author Harm op den Akker
 */
public final class DialogueNameComparator implements Comparator<String> {

	/** Shared, stateless folders-first comparator for dialogue names. */
	public static final DialogueNameComparator FOLDERS_FIRST = new DialogueNameComparator();

	private DialogueNameComparator() {
	}

	@Override
	public int compare(String a, String b) {
		String[] aParts = a.split("/");
		String[] bParts = b.split("/");
		int sharedLevels = Math.min(aParts.length, bParts.length);
		for (int i = 0; i < sharedLevels; i++) {
			boolean aInFolderHere = i < aParts.length - 1;
			boolean bInFolderHere = i < bParts.length - 1;
			if (aInFolderHere != bInFolderHere)
				return aInFolderHere ? -1 : 1;
			int levelComparison = aParts[i].compareToIgnoreCase(bParts[i]);
			if (levelComparison != 0)
				return levelComparison;
		}
		int depthComparison = Integer.compare(aParts.length, bParts.length);
		return depthComparison != 0 ? depthComparison : a.compareTo(b);
	}

}
