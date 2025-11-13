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

import com.alibaba.fluss.metadata.DatabaseDescriptor;
import org.apache.paimon.schema.Schema;

import com.alibaba.fluss.client.Connection;
import com.alibaba.fluss.client.ConnectionFactory;
import com.alibaba.fluss.client.admin.Admin;
import com.alibaba.fluss.config.Configuration;
import com.alibaba.fluss.metadata.TableDescriptor;
import com.alibaba.fluss.metadata.TablePath;
import com.alibaba.fluss.types.DataTypes;
import org.apache.paimon.schema.SchemaChange;

import java.util.List;
import java.util.Map;

/** Implementation of {@link StreamingStore} for Fluss streaming store. */
public class FlussStoreCatalog implements StreamingStore {

    private final Map<String, String> options;

    private final Connection connection;
    private final Admin admin;

    public FlussStoreCatalog(Map<String, String> options) {
        this.options = options;
        this.connection = ConnectionFactory.createConnection(Configuration.fromMap(options));
        this.admin = connection.getAdmin();
    }

    @Override
    public List<SchemaChange> createTable(
                            Identifier identifier, Schema schema, boolean ignoreIfExists) {
        try {
            admin.createDatabase("default", DatabaseDescriptor.EMPTY, true).get();
            admin.createTable(
                    TablePath.of(identifier.getDatabaseName(), identifier.getTableName()),
                    TableDescriptor.builder()
                            .schema(
                                    com.alibaba.fluss.metadata.Schema.newBuilder()
                                            .column("a", DataTypes.INT())
                                            .column("b", DataTypes.STRING())
                                            .build())
                            .customProperty("bootstrap.servers", "localhost:9123")
                            .build(),
                    ignoreIfExists).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dropTable(Identifier identifier) {
        admin.dropTable(
                TablePath.of(identifier.getDatabaseName(), identifier.getTableName()), true);
    }

    @Override
    public void close() throws Exception {
        if (admin != null) {
            admin.close();
        }
        if (connection != null) {
            connection.close();
        }
    }
}
