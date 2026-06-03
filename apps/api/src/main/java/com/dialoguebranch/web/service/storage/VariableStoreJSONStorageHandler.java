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

package com.dialoguebranch.web.service.storage;

import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableStoreChange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.io.FileUtils;
import org.slf4j.Logger;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A {@link VariableStoreJSONStorageHandler} can manage reading and writing {@link VariableStore}s
 * to and from JSON file representations. You can instantiate an instance of a {@link
 * VariableStoreJSONStorageHandler} by providing a root dataDirectory. The storage handler will
 * assume/create a single {username}.json file for every Dialogue Branch User that will contain a
 * JSON representation of the Dialogue Branch Variable Store.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class VariableStoreJSONStorageHandler implements VariableStoreStorageHandler {

    private final String dataDirectory;
    private static final Object LOCK = new Object();
    private final Logger logger =
            AppComponents.getLogger(ClassUtils.getUserClass(getClass()).getSimpleName());

    /**
     * Creates an instance of a {@link VariableStoreJSONStorageHandler} that can read and write
     * {@link VariableStore}s to and from the given {@code dataDirectory} in a JSON format.
     *
     * @param dataDirectory a string description of the directory where to read and write from.
     */
    public VariableStoreJSONStorageHandler(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public VariableStore read(User user) throws IOException, ParseException {
        synchronized (LOCK) {
            File dataDir = new File(dataDirectory);
            FileUtils.mkdir(dataDir);
            File dataFile = new File(dataDir, user.getId() + ".json");
            if (!dataFile.exists())
                return new VariableStore(user);
            ObjectMapper mapper = new ObjectMapper();

            try {
                Variable[] variables = mapper.readValue(dataFile,
                        new TypeReference<>() {});
                return new VariableStore(user, variables);
            } catch (JsonProcessingException ex) {
                throw new ParseException(
                        "Failed to parse variable store file: " +
                                dataFile.getAbsolutePath() + ": " + ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void write(VariableStore variableStore) throws IOException {
        synchronized (LOCK) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.configure(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE,
                    true);

            File dataDir = new File(dataDirectory);
            FileUtils.mkdir(dataDir);
            File dataFile = new File(dataDir,
                    variableStore.getUser().getId() + ".json");

            // Write the VariableStore only as a list of DLBVariables
            // (for easier deserialization).
            objectMapper.writeValue(dataFile,variableStore.getVariables());
        }
    }

    @Override
    public void onChange(VariableStore variableStore, List<VariableStoreChange> changes) {
        try {
            write(variableStore);
        } catch(IOException e) {
            logger.error("Failed to write variable store changes: " + e.getMessage(), e);
        }
    }

}
