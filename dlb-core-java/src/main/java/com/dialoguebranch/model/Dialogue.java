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

package com.dialoguebranch.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.model.nodepointer.ExternalNodePointer;

/**
 * Object representation of a DialogueBranch Dialogue definition. A {@link Dialogue} has a name
 * and an (unordered) list of {@link Node}s. One of these {@link Node}s should have as title
 * "Start".
 * 
 * @author Harm op den Akker (Roessingh Research and Development)
 */
public class Dialogue {
	
	private String dialogueName;
	private Map<String, Node> nodes = new LinkedHashMap<>(); // map from lower-case node titles to nodes
	private Set<String> speakers = new HashSet<>();
	private Set<String> variablesNeeded = new HashSet<>();
	private Set<String> variablesWritten = new HashSet<>();
	private Set<String> dialoguesReferenced = new HashSet<>();
	
	// ---------- Constructors:
	
	/**
	 * Creates an empty instance of a {@link Dialogue}.
	 */
	public Dialogue() {
	}
	
	/**
	 * Creates an instance of a {@link Dialogue} with a given {@code dialogueName}.
	 *
	 * @param dialogueName the name of this {@link Dialogue}.
	 */
	public Dialogue(String dialogueName) {
		this.dialogueName = dialogueName;
	}

	/**
	 * Creates an instance of a {@link Dialogue}, instantiated with the contents of the given
	 * {@code other} {@link Dialogue}.
	 *
	 * @param other the {@link Dialogue} with which to instantiate this {@link Dialogue}
	 */
	public Dialogue(Dialogue other) {
		dialogueName = other.dialogueName;
		for (String key : other.nodes.keySet()) {
			nodes.put(key, new Node(other.nodes.get(key)));
		}
		speakers.addAll(other.speakers);
		variablesNeeded.addAll(other.variablesNeeded);
		variablesWritten.addAll(other.variablesWritten);
		dialoguesReferenced.addAll(other.dialoguesReferenced);
	}
	
	// ---------- Getters:
	
	/**
	 * Returns the name of this {@link Dialogue}.
	 *
	 * @return the name of this {@link Dialogue}.
	 */
	public String getDialogueName() {
		return this.dialogueName;
	}
	
	/**
	 * Returns the starting {@link Node} for this {@link Dialogue}.
	 *
	 * @return the starting {@link Node} for this {@link Dialogue}.
	 */
	public Node getStartNode() {
		return nodes.get("start");
	}
	
	/**
	 * Returns the nodes as an unmodifiable list.
	 * 
	 * @return the nodes as an unmodifiable list
	 */
	public List<Node> getNodes() {
		return Collections.unmodifiableList(new ArrayList<>(nodes.values()));
	}

	public void addNode(Node node) {
		nodes.put(node.getTitle().toLowerCase(), node);
		if (node.getHeader().getSpeaker() != null)
			speakers.add(node.getHeader().getSpeaker());
		node.getBody().getReadVariableNames(variablesNeeded);
		node.getBody().getWriteVariableNames(variablesWritten);
		Set<NodePointer> nodePointers = new HashSet<>();
		node.getBody().getNodePointers(nodePointers);
		for (NodePointer nodePointer : nodePointers) {
			if (!(nodePointer instanceof ExternalNodePointer))
				continue;
			ExternalNodePointer extPointer = (ExternalNodePointer)nodePointer;
			dialoguesReferenced.add(extPointer.getAbsoluteTargetDialogue());
		}
	}
	
	public Set<String> getSpeakers() {
		return Collections.unmodifiableSet(speakers);
	}
	
	public List<String> getSpeakersList() {
		List<String> speakersList = new ArrayList<>(speakers);
		Collections.sort(speakersList);
		return Collections.unmodifiableList(speakersList);
	}

	public Set<String> getVariablesNeeded() {
		return Collections.unmodifiableSet(variablesNeeded);
	}
	
	public Set<String> getVariablesWritten() {
		return Collections.unmodifiableSet(variablesWritten);
	}

	/**
	 * Returns a {@link Set} of {@link String}s containing all the names of dialogues
	 * that are referenced by this {@link Dialogue}. These names do not include path and
	 * file extension information.
	 * @return all DialogueBranch dialogues referenced directly from this {@link Dialogue}.
	 */
	public Set<String> getDialoguesReferenced() {
		return Collections.unmodifiableSet(dialoguesReferenced);
	}
	
	// ---------- Setters:
	
	/**
	 * Sets the name of this {@link Dialogue}.
	 * @param dialogueName the name of this {@link Dialogue}.
	 */
	public void setDialogueName(String dialogueName) {
		this.dialogueName = dialogueName;
	}
	
	// ---------- Functions:
	
	public boolean nodeExists(String nodeId) {
		return nodes.containsKey(nodeId.toLowerCase());
	}

	/**
	 * Returns the {@link Node} with the given identifier or title.
	 * @param nodeId the node ID
	 * @return the node
	 */
	public Node getNodeById(String nodeId) {
		return nodes.get(nodeId.toLowerCase());
	}
	
	/**
	 * Returns the total number of nodes in this {@link Dialogue}.
	 *
	 * @return the total number of nodes in this {@link Dialogue}.
	 */
	public int getNodeCount() {
		return nodes.size();
	}
	
	/**
	 * Returns the total number of speakers present in this {@link Dialogue}.
	 *
	 * @return the total number of speakers present in this {@link Dialogue}.
	 */
	public int getSpeakerCount() {
		return speakers.size();
	}
	
	/**
	 * Returns the total number of different dialogues referenced from this {@link Dialogue}.
	 *
	 * @return the total number of different dialogues referenced from this {@link Dialogue}.
	 */
	public int getDialoguesReferencedCount() {
		return dialoguesReferenced.size();
	}
	
	/**
	 * Returns the total number of different variables needed in executing this {@link Dialogue}.
	 *
	 * @return the total number of different variables needed in executing this {@link Dialogue}.
	 */
	public int getVariablesNeededCount() {
		return variablesNeeded.size();
	}
	
	/**
	 * Returns the total number of different variables written in executing this {@link Dialogue}.
	 *
	 * @return the total number of different variables written in executing this {@link Dialogue}.
	 */
	public int getVariablesWrittenCount() {
		return variablesWritten.size();
	}
	
	/**
	 * Returns a human-readable multi-line summary string, representing the contents of this
	 * {@link Dialogue}.
	 */
	public String toString() {
		String summaryString = "";
		
		summaryString += "Dialogue Name: "+getDialogueName()+"\n";
		summaryString += "Number of Nodes: "+getNodeCount()+"\n";
		
		summaryString += "\n";
		
		summaryString += "Speakers present ("+getSpeakerCount()+"):\n";
		for(String s : getSpeakers()) {
			summaryString += "  - " + s + "\n";
		}
		
		summaryString += "Dialogues referenced ("+getDialoguesReferencedCount()+"):\n";
		List<String> names = new ArrayList<>(getDialoguesReferenced());
		Collections.sort(names);
		for(String s : names) {
			summaryString += "  - " + s + "\n";
		}
		
		summaryString += "Variables needed ("+getVariablesNeededCount()+"):\n";
		names = new ArrayList<>(getVariablesNeeded());
		Collections.sort(names);
		for(String s : names) {
			summaryString += "  - " + s + "\n";
		}
		
		summaryString += "Variables written ("+getVariablesWrittenCount()+"):\n";
		names = new ArrayList<>(getVariablesWritten());
		Collections.sort(names);
		for(String s : names) {
			summaryString += "  - " + s + "\n";
		}
		
		return summaryString;
	}
}
