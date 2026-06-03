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
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableStoreOnChangeListener;
import nl.rrd.utils.exception.ParseException;

import java.io.IOException;

/**
 * Interface for classes that can read and write a {@link VariableStore}.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public interface VariableStoreStorageHandler extends VariableStoreOnChangeListener {

    /**
     * Reads a {@link VariableStore} from whatever medium the implementing class is using.
     *
     * @param user the {@link User} that the {@link VariableStore} belongs to.
     * @return the {@link VariableStore} object.
     * @throws IOException in case of a read/write (or other I/O) error.
     * @throws ParseException in case the contents of the variable store could not be understood.
     */
    VariableStore read(User user) throws IOException, ParseException;

    /**
     * Write a given {@link VariableStore} to whatever medium the implementing class is using.
     *
     * @param variableStore the {@link VariableStore} to write.
     * @throws IOException in case of any I/O error with the medium used.
     */
    void write(VariableStore variableStore) throws IOException;

}
