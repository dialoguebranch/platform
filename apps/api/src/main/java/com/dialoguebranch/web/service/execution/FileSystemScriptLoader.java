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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.ResourcePointer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A {@link ScriptLoader} implementation that reads Dialogue Branch script and translation files
 * directly from a real directory on disk, laid out exactly like {@link SpringResourceScriptLoader}
 * expects on the classpath:
 * <pre>
 *   {projectRoot}/
 *     dlb-project.xml
 *     {language-code}/
 *       dialogue.dlb
 *       subfolder/
 *         other.dlb
 *     {other-language-code}/
 *       dialogue.json
 * </pre>
 *
 * <p>Used to seed a project's draft dialogues directly from a temporary directory an uploaded
 * project export {@code .zip} was extracted into (see {@code ProjectImportService}).</p>
 *
 * @author Harm op den Akker
 */
public class FileSystemScriptLoader implements ScriptLoader {

    private final File projectRoot;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /**
     * Creates a {@link FileSystemScriptLoader} for the Dialogue Branch project rooted at the given
     * directory.
     *
     * @param projectRoot the project's root directory (the one directly containing {@code
     *                     dlb-project.xml} and the per-language sub-directories).
     */
    public FileSystemScriptLoader(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    // ------------------------------------------------------ //
    // -------------------- ScriptLoader API ---------------- //
    // ------------------------------------------------------ //

    /**
     * Walks every language sub-directory of the project root and returns every {@code *.dlb} and
     * {@code *.json} file found as a {@link ResourcePointer}.
     *
     * @return the list of discovered dialogue and translation file descriptors.
     * @throws IOException if the directory tree cannot be read.
     */
    @Override
    public List<ResourcePointer> listDialogueBranchFiles() throws IOException {
        List<ResourcePointer> result = new ArrayList<>();

        File[] languageDirs = projectRoot.listFiles(File::isDirectory);
        if (languageDirs == null) return result;

        for (File languageDir : languageDirs) {
            String language = languageDir.getName();
            try (Stream<Path> paths = Files.walk(languageDir.toPath())) {
                for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                    ResourcePointer pointer = toResourcePointer(languageDir, path, language);
                    if (pointer != null) result.add(pointer);
                }
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }

        return result;
    }

    /**
     * Opens the file identified by the given {@link ResourcePointer} as a {@link Reader}.
     *
     * @param fileDescriptor the descriptor of the file to open.
     * @return a {@link Reader} over the file contents.
     * @throws IOException if the file cannot be found or opened.
     */
    @Override
    public Reader openFile(ResourcePointer fileDescriptor) throws IOException {
        String extension = fileDescriptor.getResourceType() == ResourceType.SCRIPT
                ? DialogueBranchConstants.DLB_SCRIPT_FILE_EXTENSION
                : DialogueBranchConstants.DLB_TRANSLATION_FILE_EXTENSION;
        File file = new File(new File(projectRoot, fileDescriptor.getLanguage()),
                fileDescriptor.getDialogueName() + extension);
        if (!file.isFile()) {
            throw new IOException("File not found: " + file);
        }
        return new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- //
    // -------------------- Private Helper Methods -------------------- //
    // ---------------------------------------------------------------- //

    /**
     * Derives a {@link ResourcePointer} from a file found beneath a language directory, using its
     * path relative to that language directory (with {@code File.separatorChar} normalised to
     * {@code /}) as the dialogue name, minus its extension.
     *
     * @param languageDir the language directory {@code path} was found under.
     * @param path        the file to convert.
     * @param language    the language code (the language directory's own name).
     * @return the {@link ResourcePointer}, or {@code null} if {@code path} is not a recognised
     * script or translation file.
     */
    private ResourcePointer toResourcePointer(File languageDir, Path path, String language) {
        String fileName = path.getFileName().toString();
        ResourceType resourceType;
        String extension;
        if (fileName.endsWith(DialogueBranchConstants.DLB_SCRIPT_FILE_EXTENSION)) {
            resourceType = ResourceType.SCRIPT;
            extension = DialogueBranchConstants.DLB_SCRIPT_FILE_EXTENSION;
        } else if (fileName.endsWith(DialogueBranchConstants.DLB_TRANSLATION_FILE_EXTENSION)) {
            resourceType = ResourceType.TRANSLATION;
            extension = DialogueBranchConstants.DLB_TRANSLATION_FILE_EXTENSION;
        } else {
            return null;
        }

        String relative = languageDir.toPath().relativize(path).toString()
                .replace(File.separatorChar, '/');
        String dialogueName = relative.substring(0, relative.length() - extension.length());
        if (dialogueName.isEmpty()) return null;

        return new ResourcePointer(language, dialogueName, resourceType);
    }

}
