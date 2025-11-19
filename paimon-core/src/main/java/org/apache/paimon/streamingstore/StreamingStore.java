/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.streamingstore;

import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.schema.SchemaChange;
import org.apache.paimon.table.Table;

import java.util.List;
import java.util.Map;

/**
 * Interface for managing tables in external streaming stores.
 *
 * <p>This interface provides operations to create and drop tables in streaming stores (e.g., Fluss)
 * that are used to enable second-level latency for Paimon tables. When a Paimon table is upgraded
 * to use a streaming store by setting the {@code streaming-store} table property, this interface is
 * used to create a corresponding table in the streaming store with the same schema as the Paimon
 * table.
 */
public interface StreamingStore {

    /**
     * Creates a table in the streaming store with the specified identifier and schema.
     *
     * <p>This method is called when a Paimon table is upgraded to use a streaming store. The
     * created table should have the same schema as the Paimon table to ensure data compatibility.
     *
     * @param identifier The table identifier (database and table name)
     * @param table The Paimon table to create in the streaming store
     * @return List of schema changes that were applied during table creation. This may include
     *     changes needed to adapt the Paimon table schema to the streaming store's requirements,
     *     such as adding system columns or adjusting data types.
     */
    List<SchemaChange> createTable(Identifier identifier, Table table);

    /**
     * Drops partitions from a table in the streaming store.
     *
     * <p>This method removes the specified partitions from the streaming store table. Each partition
     * is represented as a map of partition column names to their values.
     *
     * @param identifier The table identifier (database and table name)
     * @param partitions List of partitions to drop, where each partition is a map of partition
     *     column names to their values
     * @throws Catalog.TableNotExistException if the table does not exist in the streaming store
     */
    void dropPartitions(Identifier identifier, List<Map<String, String>> partitions)
            throws Catalog.TableNotExistException;

    /**
     * Alters a table in the streaming store by applying schema changes.
     *
     * <p>This method applies the specified schema changes to the table in the streaming store. The
     * changes should be compatible with the streaming store's schema evolution capabilities.
     *
     * @param identifier The table identifier (database and table name)
     * @param changes List of schema changes to apply
     * @param ignoreIfNotExists If true, the operation will be ignored if the table does not exist;
     *     if false, a {@link Catalog.TableNotExistException} will be thrown
     * @throws Catalog.TableNotExistException if the table does not exist and {@code
     *     ignoreIfNotExists} is false
     * @throws Catalog.ColumnAlreadyExistException if attempting to add a column that already exists
     * @throws Catalog.ColumnNotExistException if attempting to modify or drop a column that does not
     *     exist
     */
    void alterTable(Identifier identifier, List<SchemaChange> changes, boolean ignoreIfNotExists)
            throws Catalog.TableNotExistException, Catalog.ColumnAlreadyExistException,
                    Catalog.ColumnNotExistException;

    /**
     * Drops a table from the streaming store.
     *
     * <p>This method is called when a Paimon table is dropped. It removes the corresponding table
     * from the streaming store, including all its data and metadata.
     *
     * @param identifier The table identifier (database and table name) to drop
     * @throws RuntimeException if the table drop fails for any reason
     */
    void dropTable(Identifier identifier);
}
