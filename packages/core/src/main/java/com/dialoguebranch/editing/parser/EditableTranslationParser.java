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

package com.dialoguebranch.editing.parser;

import com.dialoguebranch.exception.FileSystemException;
import com.dialoguebranch.execution.model.Language;
import com.dialoguebranch.editing.model.EditableProject;
import com.dialoguebranch.editing.model.EditableTranslation;
import com.dialoguebranch.editing.model.FileStorageSource;
import com.dialoguebranch.editing.model.StorageSource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

/**
 * Utility class that reads an {@link EditableTranslation} from a {@link StorageSource} within an
 * {@link EditableProject}.
 *
 * @author Harm op den Akker
 */
public class EditableTranslationParser {

    /** Utility class — no instances. */
    private EditableTranslationParser() {}

    /**
     * Reads an {@link EditableTranslation} for the given {@code language} from the given
     * {@code storageSource} within the given {@code editableProject}. Dispatches to the
     * appropriate overload based on the concrete type of {@code storageSource}.
     *
     * @param editableProject the project this translation belongs to.
     * @param language the language of this translation.
     * @param storageSource the storage location of the translation file.
     * @return the parsed {@link EditableTranslation}, or {@code null} for unsupported sources.
     * @throws FileSystemException if the file is outside the project or in the wrong language folder.
     * @throws IOException if the file cannot be read.
     */
    public static EditableTranslation read(EditableProject editableProject,
                                           Language language,
                                           StorageSource storageSource) throws FileSystemException, IOException {
        if(storageSource instanceof FileStorageSource fileStorageSource) {
            return read(editableProject,language,fileStorageSource);
        } else {
            return null;
            // TODO: Implement for other StorageSources
        }
    }

    /**
     * Reads an {@link EditableTranslation} from a {@link FileStorageSource}. Validates that the
     * source file is located within the project's base path and the correct language folder before
     * reading.
     *
     * @param editableProject the project this translation belongs to.
     * @param language the language of this translation.
     * @param fileStorageSource the file-backed storage source of the translation.
     * @return the parsed {@link EditableTranslation}, or {@code null} (work in progress).
     * @throws IOException if the file cannot be read.
     * @throws FileSystemException if the file is outside the project or in the wrong language folder.
     */
    public static EditableTranslation read(EditableProject editableProject,
                                           Language language,
                                           FileStorageSource fileStorageSource)
            throws IOException, FileSystemException {
        String projectBasePath = editableProject.getProjectMetaData().getBasePath();

        // First, make sure that the given fileStorageSource points to a file that is part of
        // the given project.
        if(!(fileStorageSource.getSourceFile().getCanonicalPath()
                .contains(new File(projectBasePath).getCanonicalPath() + File.separator))) {
            throw new FileSystemException("Attempting to read a translation file that is not " +
                    "contained within the given Dialogue Branch project.");
        }

        String languageFolder = new File(projectBasePath).getCanonicalPath() + File.separator
                + language.getCode() + File.separator;

        // Next, check that the given fileStorageSource is in the correct language folder
        if(!(fileStorageSource.getSourceFile().getCanonicalPath()
                .contains(languageFolder))) {
            throw new FileSystemException("Attempting to read a translation file that is part of " +
                    "the project, but not in its correct language folder.");
        }

        // TODO: This is unfinished

        // Determine the full "dialogueName" (e.g. /subfolder/test/dialogue1")
        String dialogueName = fileStorageSource.getSourceFile().getCanonicalPath()
                .substring(languageFolder.length(),0);



        ObjectMapper mapper = new ObjectMapper();
        //this.contentMap = mapper.readValue(fileStorageSource.getSourceFile(), HashMap.class);

        return null;
    }
}
