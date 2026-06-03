/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
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

package com.dialoguebranch.script.model;

import com.dialoguebranch.model.Constants;
import com.dialoguebranch.model.ResourceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The {@link ScriptTreeNode} class is used to model a hierarchy of Dialogue Branch scripts,
 * organized in a tree-like "folder" structure. An individual {@link ScriptTreeNode} can thus
 * represent either a Dialogue Branch script, a translation file, or a folder.
 */
public class ScriptTreeNode {

    /** The parent node of this ScriptTreeNode (or null if this is the root). */
    private ScriptTreeNode parent;

    /** A pointer to the location (file or database) where this script node is stored. */
    private StorageSource storageSource;

    /** Whether this node represents a script, translation file, or folder. */
    private ResourceType resourceType;

    /** The name of this ScriptTreeNode. */
    private String name;

    /** A list of children of this ScriptTreeNode. */
    private List<ScriptTreeNode> children;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    public ScriptTreeNode(ScriptTreeNode parent, StorageSource storageSource,
                          ResourceType resourceType, String name) {
        this.parent = parent;
        this.storageSource = storageSource;
        this.resourceType = resourceType;
        this.name = name;
        this.children = new ArrayList<>();
    }

    // ----------------------------------------------------------- //
    // -------------------- Getters & Setters -------------------- //
    // ----------------------------------------------------------- //

    public ScriptTreeNode getParent() {
        return this.parent;
    }

    public void setParent(ScriptTreeNode parent) {
        this.parent = parent;
    }

    public StorageSource getStorageSource() {
        return this.storageSource;
    }

    public void setStorageSource(StorageSource storageSource) {
        this.storageSource = storageSource;
    }

    public ResourceType getResourceType() {
        return this.resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ScriptTreeNode> getChildren() {
        return this.children;
    }

    public void setChildren(List<ScriptTreeNode> children) {
        this.children = children;
        this.children.sort(new ScriptTreeNodeComparator());
    }

    // ------------------------------------------------------- //
    // -------------------- Other Methods -------------------- //
    // ------------------------------------------------------- //

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public void addChild(ScriptTreeNode node) {
        this.children.add(node);
        this.children.sort(new ScriptTreeNodeComparator());
    }

    /**
     * Returns the total number of actual scripts that exist under this {@link ScriptTreeNode}
     * (recursively). E.g. if this ScriptTreeNode represents the 'root' language directory, this
     * function will return the total number of scripts or translation files that exist in that
     * language folder. Every leaf node that is not a folder (i.e. an empty folder) is therefore
     * counted.
     *
     * @return the total number of leaf nodes under this {@link ScriptTreeNode}.
     */
    public int getTotalNumberOfScripts() {
        if(this.isLeaf() && (
                this.resourceType == ResourceType.SCRIPT
                || this.resourceType == ResourceType.TRANSLATION)) return 1;
        else {
            int childLeaves = 0;
            for(ScriptTreeNode child : children) {
                childLeaves += child.getTotalNumberOfScripts();
            }
            return childLeaves;
        }
    }

    /**
     * Returns a {@link ScriptTreeNode} that is the child of the current node and that matches the
     * given {@code name} and has the same type (folder / non-folder) as the given boolean {@code
     * isFolder} indicates. E.g. If we are looking for a folder with the name "test", we call this
     * method using "test", and "true". If we are looking for any script (translation or actual
     * script) called "startScript", we call this method using "startScript" and "false". If no
     * such node exists, this folder returns {@code null}.
     *
     * @param name the name of the child node to look for
     * @param isFolder whether the resourceType should be FOLDER or not
     * @return the child {@link ScriptTreeNode} found, or {@code null}
     */
    public ScriptTreeNode getMatchingChild(String name, boolean isFolder) {
        for(ScriptTreeNode child : this.children) {

            if(child.getName().equals(name)) {
                if(isFolder) {
                    if(child.getResourceType().equals(ResourceType.FOLDER))
                        return child;
                } else {
                    if(child.getResourceType().equals(ResourceType.SCRIPT) ||
                            child.getResourceType().equals(ResourceType.TRANSLATION)) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        if(parent == null) {
            result.append("ROOT: ");
        }

        result.append(this.getStorageSource().getDescriptor()).append(" (").append(this.getName()).append(")");

        if(!children.isEmpty()) {
            result.append(Constants.DLB_PATH_SEPARATOR + " (").append(children.size()).append(" children).");
        }

        if(this.resourceType.equals(ResourceType.SCRIPT)) {
            result.append(Constants.DLB_SCRIPT_FILE_EXTENSION);
        } else if(this.resourceType.equals(ResourceType.TRANSLATION)) {
            result.append(Constants.DLB_TRANSLATION_FILE_EXTENSION);
        } else {
            for(ScriptTreeNode child : this.getChildren()) {
                result.append("\n\t\t").append(this.getName()).append(Constants.DLB_PATH_SEPARATOR).append(child.toString());
            }
        }

        return result.toString();
    }

    private static class ScriptTreeNodeComparator implements Comparator<ScriptTreeNode> {

        @Override
        public int compare(ScriptTreeNode node1, ScriptTreeNode node2) {
            if(node1.isLeaf()) {
                if(node2.isLeaf()) {
                    return node1.getName().compareTo(node2.getName());
                } else {
                    return 1;
                }
            } else {
                if(node2.isLeaf()) {
                    return -1;
                } else {
                    return node1.getName().compareTo(node2.getName());
                }
            }
        }
    }

}
