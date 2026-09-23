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

package com.dialoguebranch.model.common;

/**
 * A {@link StorageSource} for a resource that was not read from a {@link java.io.File} (e.g. a
 * Spring classpath {@code Resource}, an uploaded archive entry, a database blob) but whose caller
 * can still supply a meaningful human-readable descriptor of where it actually came from — unlike
 * {@link FileStorageSource}, which derives its descriptor from a file's own path, this one just
 * wraps whatever descriptor the caller provides.
 *
 * @author Harm op den Akker
 */
public class DescribedStorageSource implements StorageSource {

	private final String descriptor;

	/**
	 * Creates a {@link DescribedStorageSource} with the given descriptor.
	 * @param descriptor a human-readable descriptor identifying the storage location.
	 */
	public DescribedStorageSource(String descriptor) {
		this.descriptor = descriptor;
	}

	@Override
	public String getDescriptor() {
		return this.descriptor;
	}
}
