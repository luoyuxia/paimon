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
import org.apache.paimon.schema.SchemaChange;

import java.util.List;

/**
 * Interface for managing tables in external streaming stores.
 *
 * <p>This interface provides operations to create and drop tables in streaming stores (e.g., Fluss)
 * that are used to enable second-level latency for Paimon tables. When a Paimon table is upgraded
 * to use a streaming store by setting the {@code streaming-store} table property, this interface is
 * used to create a corresponding table in the streaming store with the same schema as the Paimon
 * table.
 *
 */
public interface StreamingStore extends AutoCloseable {

    /**
     * Creates a table in the streaming store with the specified identifier and schema.
     *
     * <p>This method is called when a Paimon table is upgraded to use a streaming store. The
     * created table should have the same schema as the Paimon table to ensure data compatibility.
     *
     * @param identifier The table identifier (database and table name)
     * @param schema The table schema to create
     * @param ignoreIfExists If true, the operation should not fail if the table already exists
     * @throws RuntimeException if the table creation fails
     */
    List<SchemaChange> createTable(Identifier identifier, Schema schema, boolean ignoreIfExists);

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

