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

package com.dialoguebranch.script.parser;

import com.dialoguebranch.exception.FileSystemException;
import com.dialoguebranch.model.Language;
import com.dialoguebranch.script.model.EditableProject;
import com.dialoguebranch.script.model.EditableTranslation;
import com.dialoguebranch.script.model.FileStorageSource;
import com.dialoguebranch.script.model.StorageSource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class EditableTranslationParser {

    /**
     * Read an editable translation as part of the given editableProject and in the given language.
     * @param editableProject
     * @param storageSource
     * @return
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
