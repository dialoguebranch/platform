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

package com.dialoguebranch.model.common;

import java.io.File;

/**
 * A {@link StorageSource} implementation backed by a {@link File} on the local filesystem. The
 * descriptor returned by {@link #getDescriptor()} is the file's absolute path.
 *
 * @author Harm op den Akker
 */
public class FileStorageSource implements StorageSource {

    File sourceFile;

    /**
     * Creates a {@link FileStorageSource} that points to the given file.
     * @param sourceFile the source file.
     */
    public FileStorageSource(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    /**
     * Returns the source file for this {@link FileStorageSource}.
     * @return the source file.
     */
    public File getSourceFile() {
        return this.sourceFile;
    }

    /**
     * Returns the absolute path of the source file.
     * @return the absolute file path.
     */
    @Override
    public String getDescriptor() {
        return this.sourceFile.getAbsolutePath();
    }
}
