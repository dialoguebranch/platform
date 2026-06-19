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

package com.dialoguebranch.i18n;

import com.dialoguebranch.model.Constants;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link TranslationFile} is an object representation of a JSON file that contains translations
 * for a single Dialogue Branch script. The body of a {@link TranslationFile} consists of a mapping
 * from speakerNames to a map of {term,translation}-pairs, where the "speaker name" for the user
 * is defined in {@link SourceTranslatable#USER}, e.g.:
 *
 * <pre>
 * {
 *   "speaker1" : {
 *     "term1" : "translation1",
 *     "term2" : "translation2"
 *   },
 *   "speaker2" : {
 *     "term3" : "translation3",
 *     "term4" : "translation4"
 *   }
 *   "_user" : {
 *     "term5" : "translation5",
 *     "term6" : "translation6"
 *   }
 * }</pre>
 *
 * @author Harm op den Akker
 * @author Dennis Hofs
 */
public class TranslationFile {

	private final String fileName;
	private Map<String, Map<String,String>> contentMap;

	// ----- Constructors

	/**
	 * Creates a new, empty {@link TranslationFile} for the dialogue identified by {@code fileName}.
	 *
	 * @param fileName the name of the dialogue (without extension) that this translation file
	 *                 covers. Used when writing the file to disk via {@link #writeToDirectory}.
	 */
	public TranslationFile(String fileName) {
		this.fileName = fileName;
		this.contentMap = new HashMap<>();
	}

	// ----- Getters & Setters

	/**
	 * Returns the dialogue name (without extension) that identifies this translation file.
	 *
	 * @return the dialogue name for this translation file.
	 */
	public String getFileName() {
		return fileName;
	}

	/**
	 * Returns the full content map of this translation file: a mapping from speaker names to their
	 * respective {@code {term, translation}} pairs.
	 *
	 * @return the content map of this translation file.
	 */
	public Map<String, Map<String,String>> getContentMap() {
		return contentMap;
	}

	// ----- Functions

	/**
	 * Adds a single translation entry to this {@link TranslationFile}. If an entry for the given
	 * {@code speakerName} already exists, the new {@code term}/{@code translation} pair is appended
	 * to it; otherwise a new speaker entry is created.
	 *
	 * @param speakerName the name of the speaker, or {@link SourceTranslatable#USER} for the user.
	 * @param term        the source-language term to translate.
	 * @param translation the target-language translation of the term.
	 */
	public void addTerm(String speakerName, String term, String translation) {
		if(contentMap.containsKey(speakerName)) {
			Map<String,String> terms = contentMap.get(speakerName);
			terms.put(term,translation);
		} else {
			Map<String,String> terms = new HashMap<>();
			terms.put(term,translation);
			contentMap.put(speakerName,terms);
		}
	}

	/**
	 * Writes this {@link TranslationFile} to a file, specified by {@link #getFileName()} in the
	 * given {@code directory}.
	 *
	 * @param directory the directory in which to store the .json file output
	 * @throws IOException in case the given directory is not a directory, or another file writing
	 *                     error occurs.
	 */
	public void writeToDirectory(File directory) throws IOException {
		if(!directory.isDirectory())
			throw new IOException("The given directory parameter is not a directory.");

		// create object mapper instance
		ObjectMapper mapper = new ObjectMapper();

		// Create the json file object based on the given directory and this
		// object's fileName definition
		File jsonFile = new File(directory.getAbsolutePath() + File.separator + fileName
				+ Constants.DLB_TRANSLATION_FILE_EXTENSION);

		// Create an ObjectWriter with pretty printing
		ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());

		// convert map to JSON file
		writer.writeValue(jsonFile, contentMap);
	}

	/**
	 * Writes this {@link TranslationFile} to the given {@link File} in pretty-printed JSON format.
	 * Unlike {@link #writeToDirectory}, the caller supplies the exact output file rather than a
	 * directory.
	 *
	 * @param file the {@link File} to write this translation file to.
	 * @throws IOException in case any file writing error occurs.
	 */
	public void writeToFile(File file) throws IOException {
		// create object mapper instance
		ObjectMapper mapper = new ObjectMapper();

		// Create an ObjectWriter with pretty printing
		ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());

		// convert map to JSON file
		writer.writeValue(file, contentMap);
	}

	/**
	 * Appends the contents of this {@link TranslationFile} to the given {@link File} in
	 * tab-separated-value (TSV) format. Each row contains three tab-separated columns:
	 * {@code speakerName}, {@code term}, and {@code translation}.
	 *
	 * <p>The file is opened in append mode, so existing content is preserved.</p>
	 *
	 * @param file the TSV {@link File} to append to.
	 * @throws IOException in case any file writing error occurs.
	 */
	public void writeToTSVFile(File file) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file,true))) {
			for (Map.Entry<String, Map<String, String>> entry : contentMap.entrySet()) {
				String speakerName = entry.getKey();
				Map<String, String> terms = entry.getValue();

				for (Map.Entry<String, String> termEntry : terms.entrySet()) {
					String term = termEntry.getKey();
					String translation = termEntry.getValue();

					writer.write(speakerName + "\t" + term + "\t" + translation);
					writer.newLine();
				}
			}
			writer.flush();
		} catch (IOException ex) {
			throw ex;
		}
	}

	/**
	 * Reads and replaces this {@link TranslationFile}'s content map from the given JSON {@link
	 * File}. The JSON must conform to the speaker → {term, translation} structure described in the
	 * class-level documentation.
	 *
	 * @param file the JSON {@link File} to read from.
	 * @throws IOException in case any file reading or JSON parsing error occurs.
	 */
	public void readFromFile(File file) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		this.contentMap = mapper.readValue(file, HashMap.class);
	}

}
