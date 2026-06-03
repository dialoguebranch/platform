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

package com.dialoguebranch.parser;

import com.dialoguebranch.model.Constants;
import com.dialoguebranch.model.FileDescriptor;
import com.dialoguebranch.model.ResourceType;
import com.fasterxml.jackson.core.type.TypeReference;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.io.FileUtils;
import nl.rrd.utils.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>This DialogueBranch file loader can load files from resources on the classpath. The files
 * should be organized as language/path/to/dialogue-name.dlb or language/path/to/dialogue-name.json.
 * Languages should be encoded with ISO codes like "en" or "en_GB". Example:
 * en_GB/robin/intro.dlb</p>
 *
 * <p>The files should be specified in file "dialogues.json" in the root directory. This can
 * automatically be generated at build time. It should be structured like:</p>
 *
 *<pre>{
 *    "en_GB": [
 *        { "path": [
 *            { "to": [
 *                "dialogue1.dlb",
 *                "dialogue2.dlb",
 *                "dialogue3.json"
 *            ]},
 *        ]}
 *    }
 *}</pre>
 *
 * <p>The file contains a JSON object where the keys are ISO language codes.
 * The value is an array with directory or file entries.<br />
 * A directory entry is a JSON object with one key: the directory name. The
 * value is again an array with directory or file entries.<br />
 * A file entry is a string with the file name.</p>
 *
 * @author Dennis Hofs (RRD)
 */
public class ResourceFileLoader implements FileLoader {
	private static final String INDEX_FILE = "dialogues.json";

	private String resourcePath;

	/**
	 * Constructs a new instance.
	 *
	 * @param resourcePath the resource path (without leading or trailing slash)
	 */
	public ResourceFileLoader(String resourcePath) {
		this.resourcePath = resourcePath;
	}

	@Override
	public List<FileDescriptor> listDialogueBranchFiles() throws IOException {
		List<FileDescriptor> result = new ArrayList<>();
		String path = resourcePath + "/" + INDEX_FILE;
		InputStream input = getClass().getClassLoader().getResourceAsStream(
				path);
		try (Reader reader = new InputStreamReader(input,
				StandardCharsets.UTF_8)) {
			String json = FileUtils.readFileString(reader);
			Map<String, ?> map = JsonMapper.parse(json,
					new TypeReference<Map<String, ?>>() {});
			for (String language : map.keySet()) {
				parseLanguageValue(language, map.get(language), result);
			}
		} catch (ParseException ex) {
			throw new IOException("Failed to parse resource " + path + ": " +
					ex.getMessage(), ex);
		}
		return result;
	}

	private void parseLanguageValue(String language, Object value,
			List<FileDescriptor> files) throws ParseException {
		if (value instanceof List) {
			parseDirectoryList(language, "", (List<?>)value, files);
		} else if (value == null) {
			throw new ParseException(
					"Language value must be a list, found: null");
		} else {
			throw new ParseException("Language value must be a list, found: " +
					value.getClass().getSimpleName());
		}
	}

	private void parseDirectoryValue(String language, String prefix,
			Map<?,?> entry, List<FileDescriptor> files)
			throws ParseException {
		if (entry.size() != 1) {
			throw new ParseException(String.format(
					"Directory object must have one key with the directory name, found %s keys",
					entry.size()));
		}
		String name = (String)entry.keySet().iterator().next();
		Object value = entry.get(name);
		if (value instanceof List) {
			parseDirectoryList(language, prefix + name + "/", (List<?>)value,
					files);
		} else if (value == null) {
			throw new ParseException("Directory value must be a list, found: null");
		} else {
			throw new ParseException("Directory value must be a list, found: " +
					value.getClass().getSimpleName());
		}
	}

	private void parseDirectoryList(String language, String prefix,
			List<?> children, List<FileDescriptor> files)
			throws ParseException {
		for (Object child : children) {
			parseDirectoryChild(language, prefix, child, files);
		}
	}

	private void parseDirectoryChild(String language, String prefix,
			Object entry, List<FileDescriptor> files)
			throws ParseException {
		if (entry instanceof Map) {
			parseDirectoryValue(language, prefix, (Map<?,?>)entry, files);
		} else if (entry instanceof String) {
			parseFileValue(language, prefix, (String)entry, files);
		} else if (entry == null) {
			throw new ParseException(
					"Directory entry must be a map or string, found: null");
		} else {
			throw new ParseException(
					"Directory entry must be a map or string, found: " +
					entry.getClass().getSimpleName());
		}
	}

	private void parseFileValue(String language, String prefix, String entry,
			List<FileDescriptor> files) throws ParseException {
		String path = prefix + entry;
		if (!entry.endsWith(Constants.DLB_SCRIPT_FILE_EXTENSION) &&
				!entry.endsWith(Constants.DLB_TRANSLATION_FILE_EXTENSION)) {
			throw new ParseException(
					"File does not have extension " + Constants.DLB_SCRIPT_FILE_EXTENSION
							+ " or " + Constants.DLB_TRANSLATION_FILE_EXTENSION + ": " + path);
		}
		ResourceType resourceType;
		if(entry.endsWith(Constants.DLB_SCRIPT_FILE_EXTENSION)) {
			resourceType = ResourceType.SCRIPT;
		} else {
			resourceType = ResourceType.TRANSLATION;
		}
		files.add(new FileDescriptor(language, path, resourceType));
	}

	@Override
	public Reader openFile(FileDescriptor fileDescription) throws IOException {
		String path = resourcePath + "/" + fileDescription.getLanguage() + "/" +
				fileDescription.getFilePath();
		return new InputStreamReader(getClass().getClassLoader()
				.getResourceAsStream(path), StandardCharsets.UTF_8);
	}
}
