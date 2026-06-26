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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ScriptLoader} implementation that uses Spring's {@link PathMatchingResourcePatternResolver}
 * to discover Dialogue Branch script and translation files on the classpath. Unlike the core
 * {@link com.dialoguebranch.execution.parser.ResourceScriptLoader}, this loader does not require a
 * pre-generated {@code dialogues.json} index — it scans for {@code *.dlb} and {@code *.json} files
 * directly.
 *
 * <p>The expected classpath layout beneath the project resource root is:</p>
 * <pre>
 *   {projectResourcePath}/
 *     dlb-project.xml
 *     {language-code}/
 *       dialogue.dlb
 *       subfolder/
 *         other.dlb
 *     {other-language-code}/
 *       dialogue.json
 * </pre>
 *
 * <p>Each discovered file's classpath-relative path (after the project root) is parsed as
 * {@code {language}/{filePath}} to build the corresponding {@link ResourcePointer}.</p>
 *
 * @author Harm op den Akker
 */
public class SpringResourceScriptLoader implements ScriptLoader {

    private final String projectResourcePath;
    private final PathMatchingResourcePatternResolver resolver;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /**
     * Creates a {@link SpringResourceScriptLoader} for the Dialogue Branch project located at the
     * given classpath resource path.
     *
     * @param projectResourcePath the classpath path of the project folder, without leading or
     *                            trailing slash (e.g. {@code "dlb-projects/project-test"}).
     */
    public SpringResourceScriptLoader(String projectResourcePath) {
        this.projectResourcePath = projectResourcePath;
        this.resolver = new PathMatchingResourcePatternResolver(
                getClass().getClassLoader());
    }

    // ------------------------------------------------------ //
    // -------------------- ScriptLoader API ---------------- //
    // ------------------------------------------------------ //

    /**
     * Scans the classpath for all {@code *.dlb} and {@code *.json} files beneath the project
     * resource root and returns them as {@link ResourcePointer}s. Files that do not sit inside a
     * language sub-folder (i.e. files at the project root level) are silently skipped.
     *
     * @return the list of discovered dialogue and translation file descriptors.
     * @throws IOException if the classpath scan fails.
     */
    @Override
    public List<ResourcePointer> listDialogueBranchFiles() throws IOException {
        List<ResourcePointer> result = new ArrayList<>();

        String dlbPattern  = "classpath*:" + projectResourcePath + "/**/*"
                + DialogueBranchConstants.DLB_SCRIPT_FILE_EXTENSION;
        String jsonPattern = "classpath*:" + projectResourcePath + "/**/*"
                + DialogueBranchConstants.DLB_TRANSLATION_FILE_EXTENSION;

        for (Resource resource : resolver.getResources(dlbPattern)) {
            ResourcePointer fd = toScriptPointer(resource, ResourceType.SCRIPT);
            if (fd != null) result.add(fd);
        }
        for (Resource resource : resolver.getResources(jsonPattern)) {
            ResourcePointer fd = toScriptPointer(resource, ResourceType.TRANSLATION);
            if (fd != null) result.add(fd);
        }

        return result;
    }

    /**
     * Opens the file identified by the given {@link ResourcePointer} as a {@link Reader}.
     *
     * @param fileDescriptor the descriptor of the file to open.
     * @return a {@link Reader} over the file contents.
     * @throws IOException if the resource cannot be found or opened.
     */
    @Override
    public Reader openFile(ResourcePointer fileDescriptor) throws IOException {
        String extension = fileDescriptor.getResourceType() == ResourceType.SCRIPT
                ? DialogueBranchConstants.DLB_SCRIPT_FILE_EXTENSION
                : DialogueBranchConstants.DLB_TRANSLATION_FILE_EXTENSION;
        String path = projectResourcePath + "/" + fileDescriptor.getLanguage()
                + "/" + fileDescriptor.getDialogueName() + extension;
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Classpath resource not found: " + path);
        }
        return new InputStreamReader(input, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- //
    // -------------------- Private Helper Methods -------------------- //
    // ---------------------------------------------------------------- //

    /**
     * Derives a {@link ResourcePointer} from a Spring {@link Resource} by extracting the path
     * segment relative to the project resource root. The first segment of that relative path is
     * treated as the language code; the remainder is the file path within that language folder.
     *
     * <p>Returns {@code null} if the resource does not sit inside a language sub-folder (e.g. a
     * file at the project root), or if the URI cannot be resolved.</p>
     *
     * @param resource     the Spring resource to convert.
     * @param resourceType the type to assign ({@link ResourceType#SCRIPT} or
     *                     {@link ResourceType#TRANSLATION}).
     * @return the {@link ResourcePointer}, or {@code null} if it cannot be derived.
     */
    private ResourcePointer toScriptPointer(Resource resource, ResourceType resourceType) {
        URI uri;
        try {
            uri = resource.getURI();
        } catch (IOException e) {
            return null;
        }

        String uriString = uri.toString();
        String marker = projectResourcePath + "/";
        int markerIdx = uriString.lastIndexOf(marker);
        if (markerIdx < 0) return null;

        String relative = uriString.substring(markerIdx + marker.length());

        int slashIdx = relative.indexOf('/');
        if (slashIdx < 0) return null; // file is at project root, not inside a language folder

        String language = relative.substring(0, slashIdx);
        String filePath = relative.substring(slashIdx + 1);

        if (filePath.isEmpty()) return null;

        String extension = resourceType == ResourceType.SCRIPT
                ? DialogueBranchConstants.DLB_SCRIPT_FILE_EXTENSION
                : DialogueBranchConstants.DLB_TRANSLATION_FILE_EXTENSION;
        String dialogueName = filePath.substring(0, filePath.length() - extension.length());

        return new ResourcePointer(language, dialogueName, resourceType);
    }

}
