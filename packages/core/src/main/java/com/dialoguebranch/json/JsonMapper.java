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
 * Portions of this file are vendored from the rrd-utils library
 * (https://github.com/RoessinghResearch/rrd-utils), used under the MIT License.
 *
 * Copyright (c) 2022 Roessingh Research and Development
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

package com.dialoguebranch.json;

import com.dialoguebranch.exception.ParseException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Thin wrapper around a Jackson {@link ObjectMapper} that maps its checked and unchecked failures
 * onto {@link ParseException} (for malformed / unmappable input) or {@link RuntimeException} (for
 * conversion errors that indicate a programming mistake). Vendored from {@code rrd-utils} in #102.
 *
 * @author Dennis Hofs (RRD)
 */
public class JsonMapper {

	/**
	 * Converts the specified JSON string to an object of the specified class.
	 *
	 * @param json the JSON string
	 * @param clazz the result class
	 * @param <T> the type of object to return
	 * @return the result object
	 * @throws ParseException if a JSON parsing error occurs
	 */
	public static <T> T parse(String json, Class<T> clazz) throws ParseException {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.readValue(json, clazz);
		} catch (JsonParseException ex) {
			throw new ParseException("Can't parse JSON code: " + ex.getMessage(), ex);
		} catch (JsonMappingException ex) {
			throw new ParseException("Can't map JSON code to object: " + ex.getMessage(), ex);
		} catch (IOException ex) {
			throw new RuntimeException("I/O error when reading from string: " + ex.getMessage(),
					ex);
		}
	}

	/**
	 * Converts the specified JSON string to an object of the specified result type.
	 *
	 * @param json the JSON string
	 * @param typeRef the result type
	 * @param <T> the type of object to return
	 * @return the result object
	 * @throws ParseException if a JSON parsing error occurs
	 */
	public static <T> T parse(String json, TypeReference<T> typeRef) throws ParseException {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.readValue(json, typeRef);
		} catch (JsonParseException ex) {
			throw new ParseException("Can't parse JSON code: " + ex.getMessage(), ex);
		} catch (JsonMappingException ex) {
			throw new ParseException("Can't map JSON code to object: " + ex.getMessage(), ex);
		} catch (IOException ex) {
			throw new RuntimeException("I/O error when reading from string: " + ex.getMessage(),
					ex);
		}
	}

	/**
	 * Converts the specified JSON object (the result of parsing JSON code to a basic Java type) to
	 * an object of the specified class.
	 *
	 * @param json the JSON object
	 * @param clazz the result class
	 * @param <T> the type of object to return
	 * @return the result object
	 * @throws ParseException if the JSON object can't be converted to the specified class
	 */
	public static <T> T convert(Object json, Class<T> clazz) throws ParseException {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.convertValue(json, clazz);
		} catch (IllegalArgumentException ex) {
			throw new ParseException("Can't map JSON code to object: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Converts the specified JSON object (the result of parsing JSON code to a basic Java type) to
	 * an object of the specified result type.
	 *
	 * @param json the JSON object
	 * @param typeRef the result type
	 * @param <T> the type of object to return
	 * @return the result object
	 * @throws ParseException if the JSON object can't be converted to the specified type
	 */
	public static <T> T convert(Object json, TypeReference<T> typeRef) throws ParseException {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.convertValue(json, typeRef);
		} catch (IllegalArgumentException ex) {
			throw new ParseException("Can't map JSON code to object: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Generates a JSON string from the specified object. If the object can't be converted to JSON,
	 * this method throws a {@link RuntimeException}.
	 *
	 * @param obj the object
	 * @return the JSON string
	 */
	public static String generate(Object obj) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writeValueAsString(obj);
		} catch (JsonProcessingException ex) {
			throw new RuntimeException("Can't convert object to JSON: " + ex.getMessage(), ex);
		}
	}

}
