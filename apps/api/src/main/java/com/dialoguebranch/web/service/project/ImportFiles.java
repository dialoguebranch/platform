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

package com.dialoguebranch.web.service.project;

import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.web.service.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The file handling the script import (#339) and the translation import (#340) share: the
 * target folder, the number and size of files, and reading a file as UTF-8 text.
 *
 * @author Harm op den Akker
 */
final class ImportFiles {

	/** The most files one import may contain. */
	static final int MAX_FILES = 100;

	/** The largest file one import may contain, in bytes. */
	static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

	private ImportFiles() { }

	/**
	 * Returns the folder as a dialogue name prefix (see {@link #normalizeFolder}), or refuses it.
	 *
	 * @param folder the folder to import into; empty or {@code null} for the project root.
	 * @return the folder without leading or trailing slashes; empty for the project root.
	 * @throws BadRequestException if it isn't a valid folder.
	 */
	static String requireFolder(String folder) throws BadRequestException {
		String folderPath = normalizeFolder(folder);
		if (folderPath == null) {
			throw new BadRequestException("Invalid folder '" + folder + "': use names of " +
					"letters, digits, '_' and '-', separated by '/'.");
		}
		return folderPath;
	}

	/**
	 * Refuses an import without files or with more than {@link #MAX_FILES}.
	 *
	 * @param files the files of the import.
	 * @throws BadRequestException if there are no files or too many.
	 */
	static void requireFileCount(List<MultipartFile> files) throws BadRequestException {
		if (files == null || files.isEmpty()) {
			throw new BadRequestException("No files to import.");
		}
		if (files.size() > MAX_FILES) {
			throw new BadRequestException("Too many files: at most " + MAX_FILES + " per import.");
		}
	}

	/**
	 * Reads a file of an import as UTF-8 text, or adds why it can't to {@code problems}.
	 *
	 * @param file     the file.
	 * @param problems the problems with the file so far.
	 * @return the file's text without a leading byte order mark, or {@code null} if it is larger
	 * than {@link #MAX_FILE_SIZE_BYTES}, isn't UTF-8, or can't be read.
	 */
	static String readText(MultipartFile file, List<String> problems) {
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			problems.add("larger than " + (MAX_FILE_SIZE_BYTES / 1024) + " KB");
			return null;
		}
		try {
			return decodeUtf8(file.getBytes());
		} catch (CharacterCodingException e) {
			problems.add("not a UTF-8 text file");
		} catch (IOException e) {
			problems.add("could not be read: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Returns the folder as a dialogue name prefix, without leading or trailing slashes; empty
	 * for the project root, or {@code null} if it isn't a valid folder.
	 */
	static String normalizeFolder(String folder) {
		if (folder == null) return "";
		String trimmed = folder.trim();
		while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
		while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
		if (trimmed.isEmpty()) return "";
		return trimmed.matches(DialogueBranchParser.DIALOGUE_NAME_REGEX) ? trimmed : null;
	}

	/** The file name without any directory part a browser or client may have sent along. */
	static String baseName(String originalFilename) {
		if (originalFilename == null) return "";
		String name = originalFilename.replace('\\', '/');
		return name.substring(name.lastIndexOf('/') + 1);
	}

	/** Strict UTF-8 decoding, without a leading byte order mark. */
	static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
		String text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		return text.startsWith("﻿") ? text.substring(1) : text;
	}
}
