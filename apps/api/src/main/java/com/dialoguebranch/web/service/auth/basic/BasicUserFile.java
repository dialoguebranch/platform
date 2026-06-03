/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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

package com.dialoguebranch.web.service.auth.basic;

import com.dialoguebranch.web.service.Configuration;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.xml.AbstractSimpleSAXHandler;
import nl.rrd.utils.xml.SimpleSAXParser;
import org.slf4j.Logger;
import org.xml.sax.Attributes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link BasicUserFile} objects represents the contents of the users.xml file that contains the
 * credentials for valid users of the service when using the Native Authentication Service.
 *
 * @author Harm op den Akker
 */
public class BasicUserFile {

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * This class may be used in a static way.
	 */
	public BasicUserFile() { }

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Retrieve the {@link BasicUserCredentials} matching the given {@code username}.
	 *
	 * @param username the username of the user for whom to search.
	 * @return the {@link BasicUserCredentials} object matching the user, or {@code null} if none is
	 * 		   found.
	 */
	public static BasicUserCredentials findUser(String username) {
		List<BasicUserCredentials> users;
		try {
			users = read();
		} catch (ParseException | IOException ex) {
			throw new RuntimeException("Failed to read users.xml: " +
					ex.getMessage(), ex);
		}
		String lower = username.toLowerCase();
		for (BasicUserCredentials user : users) {
			if (user.getUsername().toLowerCase().equals(lower))
				return user;
		}
		return null;
	}

	/**
	 * Read the full list of configured users and return it as a {@link List} of {@link
	 * BasicUserCredentials} objects.
	 *
	 * @return the full list of existing users for this service.
	 * @throws ParseException in case of an error parsing the users.xml file.
	 * @throws IOException in case of an error in parsing the users.xml file.
	 */
	public static List<BasicUserCredentials> read() throws ParseException, IOException {
		Configuration config = Configuration.getInstance();
		File dataDir = new File(config.get(Configuration.DATA_DIR));
		File usersFile = new File(dataDir, "users.xml");
		SimpleSAXParser<List<BasicUserCredentials>> parser = new SimpleSAXParser<>(new XMLHandler());
		return parser.parse(usersFile);
	}

	/**
	 * Implementation of an {@link AbstractSimpleSAXHandler} for parsing users.xml files.
	 */
	private static class XMLHandler extends AbstractSimpleSAXHandler<List<BasicUserCredentials>> {
		private final List<BasicUserCredentials> users = new ArrayList<>();

		/**
		 * Parser for all XML elements of the users.xml file.
		 *
		 * @param name the name of the XML element.
		 * @param attributes the attributes of the XML element.
		 * @param parents the list of parents of the XML element.
		 * @throws ParseException in case of any parse error or unexpected element found.
		 */
		@Override
		public void startElement(String name, Attributes attributes, List<String> parents)
				throws ParseException {
			if (parents.isEmpty()) {
				if (!name.equals("users")) {
					throw new ParseException("Expected element \"users\", found: " + name);
				}
			} else if (parents.size() == 1) {
				if (!name.equals("user")) {
					throw new ParseException("Expected element \"user\", found: " + name);
				}
				startUser(attributes);
			}
		}

		/**
		 * XML Parser for the "username" element from the users.xml file.
		 *
		 * @param attributes the XML attributes.
		 * @throws ParseException in case of any parse error or missing attribute.
		 */
		private void startUser(Attributes attributes) throws ParseException {
			String username = readAttribute(attributes, "username").trim();
			if (username.isEmpty()) {
				throw new ParseException("Empty value in attribute \"username\"");
			}
			String password = readAttribute(attributes, "password");
			if (password.isEmpty()) {
				throw new ParseException("Empty value in attribute \"password\"");
			}
			String[] roles;
			String rolesString;
			try {
				rolesString = readAttribute(attributes, "roles");
				roles = rolesString.split(",");

				for(String role : roles) {
					if (!(role.equalsIgnoreCase(BasicUserCredentials.USER_ROLE_CLIENT)
							|| role.equalsIgnoreCase(BasicUserCredentials.USER_ROLE_EDITOR)
							|| role.equalsIgnoreCase(BasicUserCredentials.USER_ROLE_ADMIN))) {
						throw new ParseException(
								"Invalid role specified in \"roles\": " + role);
					}
				}
			} catch (ParseException pe) {
				rolesString = BasicUserCredentials.USER_ROLE_CLIENT;
				roles = rolesString.split(",");
				Logger logger = AppComponents.getLogger(BasicUserFile.class.getSimpleName());
                logger.warn("Warning while reading users.xml file: User role not defined " +
						"for user '{}', assuming role '{}'.", username, rolesString);
			}
			users.add(new BasicUserCredentials(username, password, roles));
		}

		@Override
		public void endElement(String name, List<String> parents) { }

		@Override
		public void characters(String ch, List<String> parents) { }

		@Override
		public List<BasicUserCredentials> getObject() {
			return users;
		}
	}

}
