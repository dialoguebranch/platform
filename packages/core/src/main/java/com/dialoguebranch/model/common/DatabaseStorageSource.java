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

package com.dialoguebranch.model.common;

/**
 * A {@link StorageSource} implementation backed by a database record. The descriptor returned by
 * {@link #getDescriptor()} is a human-readable string combining the database URL, table name, and
 * record identifier.
 *
 * @author Harm op den Akker
 */
public class DatabaseStorageSource implements StorageSource {

    private final String databaseUrl;
    private final String tableName;
    private final String recordId;

    /**
     * Creates a {@link DatabaseStorageSource} pointing to a specific record in a database table.
     *
     * @param databaseUrl the JDBC connection URL of the database.
     * @param tableName   the name of the table containing the resource.
     * @param recordId    the identifier of the record within that table.
     */
    public DatabaseStorageSource(String databaseUrl, String tableName, String recordId) {
        this.databaseUrl = databaseUrl;
        this.tableName = tableName;
        this.recordId = recordId;
    }

    /**
     * Returns the database connection URL.
     * @return the database URL.
     */
    public String getDatabaseUrl() {
        return databaseUrl;
    }

    /**
     * Returns the name of the table containing the resource.
     * @return the table name.
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the identifier of the record within the table.
     * @return the record identifier.
     */
    public String getRecordId() {
        return recordId;
    }

    /**
     * Returns a human-readable descriptor identifying the database record location, in the form
     * {@code <databaseUrl>/<tableName>/<recordId>}.
     * @return the storage descriptor.
     */
    @Override
    public String getDescriptor() {
        return databaseUrl + "/" + tableName + "/" + recordId;
    }
}
