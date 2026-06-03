/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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

package com.dialoguebranch.model.nodepointer;

import com.dialoguebranch.model.Constants;
import nl.rrd.utils.exception.ParseException;
import com.dialoguebranch.model.Dialogue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ExternalNodePointer} is a pointer to a node in another dialogue. An {@link
 * ExternalNodePointer} may be constructed by providing the origin dialogueName and nodeId, as well
 * as the target dialogue Reference and target nodeId, all of which are explained below:
 * <ul>
 *     <li>originDialogueName - the full unique dialogue name (including path!) of the dialogue from
 *     which this pointer originates</li>
 *     <li>originNodeId - the ID (or "Title") of the node from which this pointer originates</li>
 *     <li>targetDialogueReference - the reference to another dialogue as provided in the dialogue
 *     branch script (this may contain various different pathing-references, such as './' or '../'
 *     and may also contain errors.</li>
 *     <li>targetNodeId - the ID (or "Title") of the node to which is being referenced.</li>
 * </ul>
 * 
 * @author Harm op den Akker (Fruit Tree Labs)
 * @author Tessa Beinema (University of Twente)
 *
 * @see NodePointer
 */
public class ExternalNodePointer extends NodePointer {

	private final String originDialogueName;
	private final String targetDialogueReference;

	/** The absolute path (e.g. "/folder/folder/dialogue" to the target dialogue */
	private final String absoluteTargetDialogue;
	
	public ExternalNodePointer(String originDialogueName,
							   String originNodeId,
							   String targetDialogueReference,
							   String targetNodeId)
									throws ParseException {
		super(originNodeId, targetNodeId);
		this.originDialogueName = originDialogueName;
		this.targetDialogueReference = targetDialogueReference;
		this.absoluteTargetDialogue = getAbsoluteDialogueId(originDialogueName,
				targetDialogueReference);
	}

	public ExternalNodePointer(ExternalNodePointer other) {
		super(other);
		this.originDialogueName = other.getOriginDialogueName();
		this.targetDialogueReference = other.getTargetDialogueReference();
		this.absoluteTargetDialogue = other.getAbsoluteTargetDialogue();
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the identifier of the {@link Dialogue} that this pointer refers to.
	 *
	 * @return the identifier of the {@link Dialogue} that this pointer refers to.
	 */
	public String getAbsoluteTargetDialogue() {
		return this.absoluteTargetDialogue;
	}

	public String getOriginDialogueName() {
		return this.originDialogueName;
	}

	public String getTargetDialogueReference() {
		return this.targetDialogueReference;
	}

	/**
	 * This method constructs the absolute path of the target dialogue, based on the targetDialogue
	 * reference as given in the dialogue branch script (e.g. /folder/dialogueName) and the path
	 * of the originDialogue (e.g. /examples/originDialogueName), resulting in e.g.
	 * /examples/folder/dialogueName.
	 *
	 * @param originDialogue the full name of the origin dialogue (including path, e.g.
	 *                       /folder/dialogueName)
	 * @param targetDialogue the reference to the target dialogue as provided in the dialogue branch
	 *                       script (e.g. /../folder/otherDialogue).
	 * @return the absolute name of the target dialogue, including the absolute path.
	 * @throws ParseException in case of a malformed reference (e.g. /folder/) or an attempt to
	 *                        reference a dialogue below the current root (language) folder.
	 */
	private static String getAbsoluteDialogueId(String originDialogue,
												String targetDialogue) throws ParseException {

		// Example:
		// originDialogue is excerpts/bobby/dialogueName
		// targetDialogue is /gossip/otherDialogue
		// Then, absolute targetDialoguePath must be excerpts/bobby/gossip/otherDialogue

		// If the targetDialogue refers to "./folder/dialogueName", the absolute path is easy:
		if(targetDialogue.startsWith("."+Constants.DLB_PATH_SEPARATOR)) {
			String result = targetDialogue.substring(2);
			if(result.isEmpty()) {
				throw new ParseException("ExternalNodePointer refers to empty dialogue name.");
			} else {
				return result;
			}
		}

		// In all other cases, we first determine the origin path (which may be an empty list)
		List<String> originPath = new ArrayList<>();

		if(originDialogue.contains(Constants.DLB_PATH_SEPARATOR)) {
			originPath = List.of(originDialogue.split(Constants.DLB_PATH_SEPARATOR));
			// Remove the origin dialogue name to get only the path
			originPath = originPath.subList(0,originPath.size()-1);
		}

		// Remove any leading '/' from the target (as it is meaningless)
		if(targetDialogue.startsWith(Constants.DLB_PATH_SEPARATOR)) {
			targetDialogue = targetDialogue.substring(1);
		}

		// Determine the absolute target path, starting from the origin path
		List<String> targetPath = new ArrayList<>(originPath);

		// We process instances of '/' one by one (allowing e.g. /../folder/../folder shenanigans).
		while(targetDialogue.contains(Constants.DLB_PATH_SEPARATOR)) {
			if(targetDialogue.startsWith(".."+Constants.DLB_PATH_SEPARATOR)) {
				// We need to go one folder up (if there are no folders left, this is an error)
				if(targetPath.isEmpty()) throw new ParseException(
						"ExternalNodePointer references a dialogue below the project root.");
				// Remove the last entry in the target path
				targetPath.remove(targetPath.size()-1);

				// Remove the "../" we just processed from the targetDialogue
				targetDialogue = targetDialogue.substring(3);
			}

			// We still have / characters, but they are not ../, so they must be folder/
			else {
				String folder = targetDialogue.substring(0,targetDialogue.indexOf(Constants.DLB_PATH_SEPARATOR));
				targetPath.add(folder);
				targetDialogue = targetDialogue.substring(targetDialogue.indexOf(Constants.DLB_PATH_SEPARATOR)+1);
			}
		}

		// What is left of targetDialogue is just the dialogueName
		if(targetDialogue.isEmpty()) {
			throw new ParseException("ExternalNodePointer refers to empty dialogue name.");
		}

		// If we have something left, we can now construct the absolute path
		StringBuilder absoluteTargetDialogue = new StringBuilder();
		for(String pathEntry : targetPath) {
			absoluteTargetDialogue.append(pathEntry)
					.append(Constants.DLB_PATH_SEPARATOR);
		}
		absoluteTargetDialogue.append(targetDialogue);

		return absoluteTargetDialogue.toString();
	}

	// ---------- Functions:

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + absoluteTargetDialogue.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj))
			return false;
		ExternalNodePointer other = (ExternalNodePointer)obj;
        return absoluteTargetDialogue.equals(other.getAbsoluteTargetDialogue());
    }

	@Override
	public ExternalNodePointer clone() {
		return new ExternalNodePointer(this);
	}

}
