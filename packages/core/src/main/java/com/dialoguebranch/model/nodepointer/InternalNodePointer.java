/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.model.nodepointer;

/**
 * An {@link InternalNodePointer} represents a pointer from an originNode to a targetNode within
 * the same Dialogue Branch script.
 * 
 * @author Tessa Beinema
 * @author Harm op den Akker
 *
 * @see NodePointer
 */
public class InternalNodePointer extends NodePointer {
	
	/**
	 * Creates an {@link InternalNodePointer} from {@code originNodeId} to {@code targetNodeId}
	 * within the same Dialogue Branch script.
	 *
	 * @param originNodeId the identifier of the node from which this pointer originates.
	 * @param targetNodeId the identifier of the node to which this pointer points.
	 */
	public InternalNodePointer(String originNodeId, String targetNodeId) {
		super(originNodeId, targetNodeId);
	}

	/**
	 * Creates a copy of the given {@link InternalNodePointer}.
	 *
	 * @param other the pointer to copy.
	 */
	public InternalNodePointer(InternalNodePointer other) {
		super(other);
	}
	
	// --------------------------------------------------- //
	// -------------------- Functions -------------------- //
	// --------------------------------------------------- //

	@Override
	public InternalNodePointer clone() {
		return new InternalNodePointer(this);
	}
}
