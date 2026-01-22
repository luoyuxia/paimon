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

package org.apache.paimon.catalog;

import org.apache.paimon.schema.Schema;

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
public interface StreamStore extends AutoCloseable {

    /**
     * Creates a table in the streaming store with the specified identifier and schema.
     *
     * <p>This method is called when a Paimon table is upgraded to use a stream store. The created
     * table should have the same schema as the Paimon table to ensure data compatibility.
     *
     * @param identifier The table identifier (database and table name)
     * @param schema The table schema to create
     * @throws RuntimeException if the table creation fails
     */
    void createTable(Identifier identifier, Schema schema);

    /**
     * Drops partitions from a table in the stream store.
     *
     * <p>This method removes the specified partitions from the stream store table. Each partition
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
     * Drops a table from the streaming store.
     *
     * <p>This method is called when a Paimon table is dropped
     *
     * @param identifier The table identifier (database and table name) to drop
     * @throws RuntimeException if the table drop fails
     */
    void dropTable(Identifier identifier);
}
