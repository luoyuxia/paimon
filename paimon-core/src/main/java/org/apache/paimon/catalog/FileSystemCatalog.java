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

import org.apache.paimon.CoreOptions;
import org.apache.paimon.fs.FileIO;
import org.apache.paimon.fs.Path;
import org.apache.paimon.operation.Lock;
import org.apache.paimon.options.Options;
import org.apache.paimon.schema.Schema;
import org.apache.paimon.schema.SchemaChange;
import org.apache.paimon.schema.SchemaManager;
import org.apache.paimon.schema.TableSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.apache.paimon.CoreOptions.STREAMING_STORE;
import static org.apache.paimon.options.CatalogOptions.CASE_SENSITIVE;

/** A catalog implementation for {@link FileIO}. */
public class FileSystemCatalog extends AbstractCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemCatalog.class);


    private final Path warehouse;

    public FileSystemCatalog(FileIO fileIO, Path warehouse) {
        super(fileIO);
        this.warehouse = warehouse;
    }

    public FileSystemCatalog(FileIO fileIO, Path warehouse, CatalogContext context) {
        super(fileIO, context);
        this.warehouse = warehouse;
    }

    @Override
    public List<String> listDatabases() {
        return uncheck(() -> listDatabasesInFileSystem(warehouse));
    }

    @Override
    protected void createDatabaseImpl(String name, Map<String, String> properties) {
        if (properties.containsKey(Catalog.DB_LOCATION_PROP)) {
            throw new IllegalArgumentException(
                    "Cannot specify location for a database when using fileSystem catalog.");
        }
        if (!properties.isEmpty()) {
            LOG.warn(
                    "Currently filesystem catalog can't store database properties, discard properties: {}",
                    properties);
        }

        Path databasePath = newDatabasePath(name);
        if (!uncheck(() -> fileIO.mkdirs(databasePath))) {
            throw new RuntimeException(
                    String.format(
                            "Create database location failed, " + "database: %s, location: %s",
                            name, databasePath));
        }
    }

    @Override
    public Database getDatabaseImpl(String name) throws DatabaseNotExistException {
        if (!uncheck(() -> fileIO.exists(newDatabasePath(name)))) {
            throw new DatabaseNotExistException(name);
        }
        return Database.of(name);
    }

    @Override
    protected void dropDatabaseImpl(String name) {
        Path databasePath = newDatabasePath(name);
        if (!uncheck(() -> fileIO.delete(databasePath, true))) {
            throw new RuntimeException(
                    String.format(
                            "Delete database failed, " + "database: %s, location: %s",
                            name, databasePath));
        }
    }

    @Override
    protected void alterDatabaseImpl(String name, List<PropertyChange> changes)
            throws DatabaseNotExistException {
        throw new UnsupportedOperationException("Alter database is not supported.");
    }

    @Override
    protected List<String> listTablesImpl(String databaseName) {
        return uncheck(() -> listTablesInFileSystem(newDatabasePath(databaseName)));
    }

    @Override
    public TableSchema loadTableSchema(Identifier identifier) throws TableNotExistException {
        return tableSchemaInFileSystem(
                        getTableLocation(identifier), identifier.getBranchNameOrDefault())
                .orElseThrow(() -> new TableNotExistException(identifier));
    }

    @Override
    protected void dropTableImpl(Identifier identifier, List<Path> externalPaths) {
        Path path = getTableLocation(identifier);
        uncheck(() -> fileIO.delete(path, true));
        for (Path externalPath : externalPaths) {
            uncheck(() -> fileIO.delete(externalPath, true));
        }
    }

    @Override
    public void createTableImpl(Identifier identifier, Schema schema) {
        SchemaManager schemaManager = schemaManager(identifier);

        Map<String, String> options = new HashMap<>();
        options.put("bootstrap.servers", "localhost:9123");
        try {
            try (FlussStoreCatalog flussStoreCatalog = new FlussStoreCatalog(options)) {
                flussStoreCatalog.createTable(identifier, schema, true);
            }
            runWithLock(identifier, () -> uncheck(() -> schemaManager.createTable(schema)));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T runWithLock(Identifier identifier, Callable<T> callable) throws Exception {
        Optional<CatalogLockFactory> lockFactory = lockFactory();
        try (Lock lock =
                lockFactory
                        .map(factory -> factory.createLock(lockContext().orElse(null)))
                        .map(l -> Lock.fromCatalog(l, identifier))
                        .orElseGet(Lock::empty)) {
            return lock.runWithLock(callable);
        }
    }

    private SchemaManager schemaManager(Identifier identifier) {
        Path path = getTableLocation(identifier);
        return new SchemaManager(fileIO, path, identifier.getBranchNameOrDefault());
    }

    @Override
    public void renameTableImpl(Identifier fromTable, Identifier toTable) {
        Path fromPath = getTableLocation(fromTable);
        Path toPath = getTableLocation(toTable);
        if (!uncheck(() -> fileIO.rename(fromPath, toPath))) {
            throw new RuntimeException(
                    String.format("Failed to rename table %s to table %s.", fromTable, toTable));
        }
    }

    @Override
    protected void alterTableImpl(Identifier identifier, List<SchemaChange> changes)
            throws TableNotExistException, ColumnAlreadyExistException, ColumnNotExistException {
        // Check if this is a Fluss server callback by checking for a temporary marker
        // We use a special option key to mark that this is a Fluss server callback
        boolean isFlussCallback = false;
        for (SchemaChange change : changes) {
            if (change instanceof SchemaChange.SetOption) {
                SchemaChange.SetOption setOption = (SchemaChange.SetOption) change;
                // Check if this change includes the Fluss callback marker
                if ("fluss.callback".equals(setOption.key())) {
                    isFlussCallback = true;
                    // Remove the marker from changes before committing
                    changes = new ArrayList<>(changes);
                    changes.remove(change);
                    break;
                }
            }
        }
        
        if (isFlussCallback) {
            // This is a callback from Fluss server, just commit the schema changes
            SchemaManager schemaManager = schemaManager(identifier);
            try {
                runWithLock(identifier, () -> schemaManager.commitChanges(changes));
            } catch (TableNotExistException
                    | ColumnAlreadyExistException
                    | ColumnNotExistException
                    | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }
        
        // Check if we need to notify Fluss server
        try {
            TableSchema currentSchema = loadTableSchema(identifier);
            Options currentOptions = Options.fromMap(currentSchema.options());
            CoreOptions.StreamingStore currentStreamingStore = currentOptions.get(STREAMING_STORE);
            
            // Check if we're setting streaming-store to 'fluss' in the changes
            CoreOptions.StreamingStore newStreamingStore = currentStreamingStore;
            boolean isSettingFluss = false;
            for (SchemaChange change : changes) {
                if (change instanceof SchemaChange.SetOption) {
                    SchemaChange.SetOption setOption = (SchemaChange.SetOption) change;
                    if (STREAMING_STORE.key().equals(setOption.key())) {
                        try {
                            newStreamingStore = CoreOptions.StreamingStore.valueOf(setOption.value().toUpperCase());
                            isSettingFluss = (newStreamingStore == CoreOptions.StreamingStore.FLUSS);
                        } catch (IllegalArgumentException e) {
                            // Invalid streaming store value, ignore
                        }
                    }
                }
            }
            
            // If we're setting streaming-store to 'fluss' and it's not already set,
            // we need to notify Fluss server first, then let Fluss server call back to commit schema
            if (isSettingFluss && currentStreamingStore != CoreOptions.StreamingStore.FLUSS) {
                // Extract Fluss options (all options starting with "fluss.")
                Map<String, String> flussOptions = new HashMap<>();
                for (SchemaChange change : changes) {
                    if (change instanceof SchemaChange.SetOption) {
                        SchemaChange.SetOption setOption = (SchemaChange.SetOption) change;
                        String key = setOption.key();
                        if (key.startsWith("fluss.")) {
                            flussOptions.put(key.substring(6), setOption.value());
                        }
                    }
                }
                // Also get existing fluss options from current schema
                for (Map.Entry<String, String> entry : currentSchema.options().entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("fluss.") && !flussOptions.containsKey(key.substring(6))) {
                        flussOptions.put(key.substring(6), entry.getValue());
                    }
                }
                
                // Send request to Fluss server
                // Fluss server will create the table and then call back to commit schema changes
                // The callback should include 'fluss.callback' = 'true' marker to identify it
                try (FlussStoreCatalog flussStoreCatalog = new FlussStoreCatalog(flussOptions)) {
                    // TODO: Add a method to FlussStoreCatalog to notify Fluss server about schema changes
                    // The actual implementation should send HTTP request to Fluss server
                    // The request should tell Fluss server to include 'fluss.callback' = 'true' when calling back
                    LOG.info("Notifying Fluss server about schema change for table {}", identifier);
                    // flussStoreCatalog.notifySchemaChange(identifier, currentSchema, changes);
                } catch (Exception e) {
                    LOG.warn("Failed to notify Fluss server about schema change", e);
                    throw e;
                }
                
                // Don't commit schema changes here, let Fluss server call back to commit
                // Fluss server should include 'fluss.callback' = 'true' in the schema changes
                return;
            }
        } catch (TableNotExistException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Failed to check streaming-store option, continuing with normal schema update", e);
            // Continue with normal schema update if check fails
        }
        
        // Normal schema update path
        SchemaManager schemaManager = schemaManager(identifier);
        try {
            runWithLock(identifier, () -> schemaManager.commitChanges(changes));
        } catch (TableNotExistException
                | ColumnAlreadyExistException
                | ColumnNotExistException
                | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static <T> T uncheck(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {}

    @Override
    public String warehouse() {
        return warehouse.toString();
    }

    @Override
    public CatalogLoader catalogLoader() {
        return new FileSystemCatalogLoader(fileIO, warehouse, context);
    }

    @Override
    public boolean caseSensitive() {
        return context.options().getOptional(CASE_SENSITIVE).orElse(true);
    }
}
