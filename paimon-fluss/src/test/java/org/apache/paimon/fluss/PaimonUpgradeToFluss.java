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

package org.apache.paimon.fluss;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.paimon.CoreOptions;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogTestBase;
import org.apache.paimon.catalog.FileSystemCatalog;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.fs.Path;
import org.apache.paimon.options.Options;
import org.apache.paimon.schema.Schema;
import org.apache.paimon.schema.SchemaChange;
import org.apache.paimon.table.FileStoreTable;
import org.apache.paimon.table.Table;
import org.apache.paimon.types.DataTypes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Tests for {@link FileSystemCatalog} to upgrade Paimon to Fluss. */
public class PaimonUpgradeToFluss extends CatalogTestBase {

    protected TableEnvironment tEnv;
    protected TableEnvironment sEnv;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        catalog =
                new FileSystemCatalog(
                        fileIO, new Path(warehouse), CatalogContext.create(new Options()));

        tEnv = TableEnvironment.create(
                EnvironmentSettings.newInstance().inBatchMode().build());
        String catalog = "PAIMON";

        Map<String, String> options = new HashMap<>();
        options.put("type", "paimon");
        options.put("warehouse", warehouse);
        tEnv.executeSql(
                String.format(
                        "CREATE CATALOG %s WITH (" + "%s" + ")",
                        catalog,
                        options.entrySet().stream()
                                .map(e -> String.format("'%s'='%s'", e.getKey(), e.getValue()))
                                .collect(Collectors.joining(","))));
        tEnv.useCatalog(catalog);

        sEnv = TableEnvironment.create(
                EnvironmentSettings.newInstance().inStreamingMode().build());
        sEnv.registerCatalog(catalog, tEnv.getCatalog(catalog).get());
        sEnv.useCatalog(catalog);
    }

    @Test
    void testUpgradeToFluss() throws Exception {
        catalog.createDatabase("fluss", false);
        Identifier identifier = Identifier.create("fluss", "t1");
        Schema schema =
                Schema.newBuilder()
                        .column("c1", DataTypes.INT())
                        .column("c2", DataTypes.STRING())
                        .column("c3", DataTypes.STRING())
                        .build();
        catalog.createTable(identifier, schema, false);

        List<SchemaChange> schemaChanges = new ArrayList<>();
        schemaChanges.add(SchemaChange.setOption(CoreOptions.STREAMING_STORE.key(), "fluss"));
        schemaChanges.add(SchemaChange.setOption("fluss.bootstrap.servers", "localhost:57989"));

        catalog.alterTable(
                identifier, schemaChanges,
                false);

        Table table =
        catalog.getTable(identifier);

        System.out.println(((FileStoreTable) table).schema());

        tEnv.executeSql("insert into fluss.t1 values (1, 'c21', 'c31'), (2, 'c2', 'c3'), (3, 'c2', 'c3')," +
                        " (4, 'c2', 'c3'), (5, 'c2', 'c3'), (6, 'c2', 'c3')")
                .await();

        sEnv.executeSql("select * from fluss.t1").print();
    }
}
