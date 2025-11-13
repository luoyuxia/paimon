# PIP-XXX: Upgrade Existing Paimon Table to Fluss Lake Table for Second-Level Latency

## Motivation

Currently, Paimon tables provide good data freshness with minute-level latency, which meets the requirements for most batch and near-real-time scenarios. However, for scenarios requiring second-level data freshness, the existing snapshot-based architecture cannot achieve second-level latency. Users need a way to upgrade their existing minute-level latency Paimon tables to second-level latency by leveraging an external streaming storage (In this PIP, we will only focus on Fluss) without losing historical data or requiring complex migration procedures.

This PIP is aimed to:

1. **Introduce a pluggable mechanism** to enable Paimon to leverage external streaming storages and Fluss implementation.

2. **Introduce a seamless upgrade path** without:
   - Losing historical data
   - Requiring complex data migration
   - Breaking existing read/write pipelines

## User-facing Changes

### 1. Table Property Configuration

Users can now configure a table to use Fluss as the streaming store by setting the `streaming-store` table property:

```sql
ALTER TABLE orders SET TBLPROPERTIES (
    'streaming-store' = 'fluss',
    'fluss.bootstrap.servers' = 'localhost:9123'
);
```

**Key Properties:**
- `streaming-store`: Set to `'fluss'` to enable Fluss integration
- `fluss.bootstrap.servers`: Required Fluss bootstrap servers configuration
- `fluss.*`: Any additional Fluss-specific configuration options prefixed with `fluss.`

### 2. Upgrade Workflow

Users follow a simple three-step process to upgrade an existing table:

**Step 1: Stop Write Pipelines**
```bash
# Stop all write pipelines for the target table
# This ensures no writes go directly to Paimon after upgrade
```

**Step 2: Execute Upgrade DDL**
```sql
ALTER TABLE orders SET TBLPROPERTIES (
    'streaming-store' = 'fluss',
    'fluss.bootstrap.servers' = 'localhost:9123'
);
```

**Step 3: Restart Write Pipelines**
```bash
# Restart write pipelines with stateless restart
# The topology changes from writing to Paimon to writing to Fluss
```

### 3. Transparent Read Behavior

After upgrade, read operations automatically use **Union Read** to combine data from both sources by default:

- **Historical Data**: Read from Paimon snapshots (existing behavior)
- **Real-time Data**: Read from Fluss streaming store (new behavior)

**No code changes required** - existing read queries continue to work:

```sql
-- This query automatically reads from both Paimon and Fluss (default behavior)
SELECT * FROM orders WHERE order_date = '2024-01-01';
```

**Batch Scan Mode for OLAP Engines:**

For OLAP engines like StarRocks and Hologress that are sensitive to query latency, and where Union Read performance is suboptimal (e.g., StarRocks currently has poor Union Read performance), users can disable Union Read by setting the `batch-scan-streaming-store` table option to `false`. When set to `false`, reads will only access Paimon's own data, bypassing the Fluss streaming store:

```sql
-- Disable Union Read for batch scan operations
ALTER TABLE orders SET TBLPROPERTIES (
    'batch-scan-streaming-store' = 'false'
);

-- Now queries will only read from Paimon snapshots
SELECT * FROM orders WHERE order_date = '2024-01-01';
```

**Key Points:**
- `batch-scan-streaming-store` defaults to `true` (Union Read enabled)
- When set to `false`, only Paimon data is read, providing better query performance for OLAP engines
- This option is particularly useful for StarRocks/Hologress integration where Union Read performance is a concern
- Real-time data in Fluss will not be included in queries when this option is `false`

### 4. Transparent Write Behavior

After upgrade, all write operations automatically route to Fluss:

- **Streaming Writes**: Data goes to Fluss instead of directly to Paimon
- **Batch Writes**: Data goes to Fluss instead of directly to Paimon
- **Tiering Service**: Fluss automatically tiers data to Paimon asynchronously

**No code changes required** - existing write pipelines continue to work:

```sql
-- This insert automatically goes to Fluss
INSERT INTO orders VALUES (1, 'product1', 100.0);
```

### 5. Schema Evolution Support

Schema evolution operations (add/drop columns, change types) work seamlessly with Fluss tables:

```sql
-- Add a new column - works for both Paimon and Fluss
ALTER TABLE orders ADD COLUMN discount DECIMAL(10,2);

-- The schema change is automatically synchronized to Fluss
```

### 6. Permission Requirements

**Current Requirements:**
- Users need to apply for Fluss-side permissions before upgrading tables
- No automatic permission synchronization (future enhancement)

**Future Enhancement:**
- Integration with DLF permissions for automatic synchronization
- No separate Fluss permission application needed

### 7. Monitoring and Observability

Users can monitor the upgrade status and Fluss integration through:

- Table properties showing `streaming-store=fluss`
- Metrics for Fluss write operations
- Metrics for union read operations (Paimon + Fluss)
- Tiering Service status and progress

### 8. Supported Table Types for Fluss Lake Table Upgrade

The following table summarizes which Paimon table types support upgrade to Fluss lake tables:

| Paimon Table Type | Supports Upgrade to Fluss Lake Table | Notes |
|-------------------|--------------------------------------|-------|
| **Append-Only** | ✅ **Supported** | • For Append-Only tables, the bucket count is `-1`<br>• Users can set `fluss.bucket.num` to specify the bucket count on the Fluss side |
| **Primary Key Table with Fixed Bucket**<br>(All partitions have the same bucket count) | ✅ **Supported** | • All partitions must have the same bucket count |
| **Primary Key Table with Fixed Bucket**<br>(Different partitions have different bucket counts) | ❌ **Not Supported** | • Currently not supported because Fluss does not support different bucket counts across partitions<br>• **Future**: Fluss will support this feature in the future |
| **Primary Key Table**<br>(HASH_DYNAMIC & KEY_DYNAMIC) | ❌ **Not Supported** | • Dynamic bucket modes are not supported |
| **Primary Key Table with POSTPONE Bucket** | ❌ **Not Supported** | • **Workaround**: DLF converts POSTPONE bucket tables to Fixed bucket tables, then users can upgrade to Fluss lake tables<br>• **Rescale Limitation**: After upgrading to Fluss lake table, DLF currently does not support rescaling the table<br>• **Future**: Once Fluss supports rescale for primary key tables, DLF and Fluss will jointly support table rescale |

**Key Points:**
- **Append-Only tables** and **Primary Key tables with fixed bucket** (where all partitions have the same bucket count) are fully supported
- Tables with dynamic bucket modes or variable bucket counts across partitions are not currently supported
- For POSTPONE bucket tables, users must first convert them to Fixed bucket tables before upgrading
- The upgrade process preserves the original table type and semantics where supported
- Union Read behavior adapts to the table type (merge for primary key tables, concatenation for append-only tables)

### 9. Backward Compatibility

**Existing Tables:**
- Tables without `streaming-store` property continue to work exactly as before
- No breaking changes to existing functionality

**Existing Code:**
- All existing read/write APIs continue to work without modification
- No application code changes required for upgrade

## Public Interfaces

### New Table Properties

- **`streaming-store`**: Enum type property that specifies the streaming store implementation. Currently supports:
  - `fluss`: Use Fluss as the streaming store for second-level latency

- **`fluss.bootstrap.servers`**: Required when `streaming-store=fluss`. Specifies the Fluss bootstrap servers (e.g., `localhost:9123`)

- **`fluss.*`**: Additional Fluss-specific configuration options can be prefixed with `fluss.` and will be passed to Fluss client

- **`batch-scan-streaming-store`**: Boolean property (default: `true`) that controls whether batch scan operations should read from the streaming store. When set to `false`, batch scans will only read from Paimon snapshots, bypassing the streaming store. This is particularly useful for OLAP engines like StarRocks and Hologress where Union Read performance is a concern.

### Modified DDL Syntax

The existing `ALTER TABLE ... SET TBLPROPERTIES` syntax is extended to support upgrading tables to Fluss lake tables:

```sql
ALTER TABLE table_name SET TBLPROPERTIES (
    'streaming-store' = 'fluss',
    'fluss.bootstrap.servers' = 'localhost:9123'
);
```

### Catalog Interface Changes

- **`StreamingStore`**: Interface for managing tables in external streaming stores. The `FlussStoreCatalog` implementation handles Fluss-specific operations.

- **`StreamingStoreFactory`**: Factory interface for creating `StreamingStore` instances. Implementations are discovered via SPI using the streaming store identifier.

- **`Catalog.alterTable()`**: Enhanced to detect when `streaming-store` is being set to `fluss` and coordinate with Fluss server to create the corresponding table.

### Flink Integration Interfaces

- **`FlinkCatalog.getTable()`**: Enhanced to detect streaming store tables and delegate to the streaming store's Flink catalog via `CatalogFactory` discovery.

- **`AbstractFlinkTableFactory.createDynamicTableSource()`**: Enhanced to detect streaming store tables and delegate to the streaming store's `DynamicTableSourceFactory` via SPI discovery.

- **`AbstractFlinkTableFactory.createDynamicTableSink()`**: Enhanced to detect streaming store tables and delegate to the streaming store's `DynamicTableSinkFactory` via SPI discovery.

### Spark Integration Interfaces

- **`StreamingStoreSparkTableFactory`**: New interface for streaming stores to provide Spark table implementations. Registered via SPI using the streaming store identifier.

```java
public interface StreamingStoreSparkTableFactory extends Factory {
    String factoryIdentifier();
    
    org.apache.spark.sql.connector.catalog.Table createTable(
        String tableName, 
        Map<String, String> properties);
}
```

- **`SparkCatalog.loadTable()`**: Enhanced to detect streaming store tables and delegate to the streaming store's `StreamingStoreSparkTableFactory` via SPI discovery.

### Read Interface

- **Union Read**: Automatically enabled when a table has `streaming-store=fluss` and `batch-scan-streaming-store=true` (default). The read path seamlessly combines:
  - Historical data from Paimon snapshots
  - Real-time data from Fluss streaming store

- **Batch Scan Mode**: When `batch-scan-streaming-store=false`, batch scan operations will only read from Paimon snapshots, providing better query performance for OLAP engines where Union Read performance is suboptimal.

No changes required to existing read APIs - the read behavior is transparent to users and configurable via table properties.

### Write Interface

- **Write Path**: When `streaming-store=fluss` is set, all writes automatically route to Fluss instead of directly to Paimon. The Fluss Tiering Service handles writing data to Paimon asynchronously.

No changes required to existing write APIs - the routing is transparent to users.

## Proposed Changes

### 1. Table Schema Evolution

When a user executes `ALTER TABLE ... SET TBLPROPERTIES ('streaming-store' = 'fluss', ...)`, the following process occurs:

1. **Schema Validation**: The catalog validates that required Fluss properties (e.g., `fluss.bootstrap.servers`) are provided.

2. **Fluss Table Creation**: The catalog notifies the Fluss server to create a corresponding table with the same schema as the Paimon table. This is done through the `FlussStoreCatalog` interface.

3. **Schema Commit**: After Fluss confirms table creation, the schema changes are committed to the Paimon table metadata, including:
   - `streaming-store = 'fluss'`
   - All `fluss.*` properties

4. **Coordination**: The Fluss server may call back to Paimon to commit schema changes, ensuring both systems stay in sync.

### 2. Changes in Write & Read Path

The core idea is to provide a **Hook interface mechanism** that allows Paimon to delegate read/write operations to streaming store implementations (e.g., Fluss) when a table is configured with `streaming-store`. This approach minimizes code changes in Paimon itself and avoids code duplication by directly reusing streaming store connector implementations (e.g., `fluss-connector-flink`).

#### 2.1 Design Philosophy

- **Pluggable Hook Mechanism**: When a table has `streaming-store` configured, Paimon's catalog and table factory implementations detect this and delegate to the streaming store's native connectors
- **Minimal Intrusion**: Changes to Paimon core code are minimal - only adding detection logic and delegation hooks
- **Code Reuse**: Directly reuse existing streaming store connector implementations (e.g., Fluss's Flink source/sink) instead of reimplementing functionality
- **Transparent to Users**: The delegation is transparent - users continue to use Paimon's APIs, but the actual read/write operations are handled by the streaming store connectors

#### 2.2 Flink Read/Write Path Changes

##### 2.2.1 Enhanced `FlinkCatalog.getTable()` Method

The `FlinkCatalog.getTable()` method is enhanced to detect streaming store tables and delegate to the streaming store's Flink catalog:

```java
public CatalogBaseTable getTable(ObjectPath tablePath) {
    // Load table schema from Paimon catalog
    Table table = catalog.getTable(toIdentifier(tablePath));
    
    // Check if this table uses a streaming store
    CoreOptions.StreamingStore streamingStore = 
        CoreOptions.fromMap(table.options()).get(STREAMING_STORE);
    
    if (streamingStore != null) {
        // Discover the streaming store's CatalogFactory via SPI
        CatalogFactory catalogFactory = FactoryUtil.discoverFactory(
            classLoader, CatalogFactory.class, streamingStore.toString());
        
        // Extract streaming store options (remove prefix, e.g., "fluss." -> "")
        Map<String, String> streamingStoreOptions = extractStreamingStoreOptions(
            table.options(), streamingStore.toString());
        
        // Create streaming store catalog and get table
        org.apache.flink.table.catalog.Catalog streamingStoreCatalog = 
            catalogFactory.createCatalog(createCatalogContext(streamingStoreOptions));
        streamingStoreCatalog.open();
        
        try {
            return streamingStoreCatalog.getTable(
                new ObjectPath(tablePath.getDatabaseName(), tablePath.getObjectName()));
        } finally {
            streamingStoreCatalog.close();
        }
    }
    
    // Normal Paimon table path
    return toCatalogTable(table);
}
```

**Key Points**:
- The method checks if `streaming-store` property is set in the table options
- If set, it discovers the streaming store's `CatalogFactory` via SPI using the streaming store identifier
- Extracts and transforms streaming store options (removes prefix like `fluss.`)
- Creates the streaming store's Flink catalog and delegates `getTable()` to it
- Returns the streaming store's `CatalogBaseTable` directly

##### 2.2.2 Enhanced `AbstractFlinkTableFactory.createDynamicTableSource()` Method

The `AbstractFlinkTableFactory.createDynamicTableSource()` method is enhanced to delegate to streaming store's source factory:

```java
public DynamicTableSource createDynamicTableSource(Context context) {
    Map<String, String> options = context.getCatalogTable().getOptions();
    CoreOptions.StreamingStore streamingStore = 
        Options.fromMap(options).get(STREAMING_STORE);
    
    if (streamingStore != null) {
        // Discover streaming store's DynamicTableSourceFactory via SPI
        DynamicTableSourceFactory sourceFactory = FactoryUtil.discoverFactory(
            context.getClassLoader(), 
            DynamicTableSourceFactory.class, 
            streamingStore.toString());
        
        // Extract streaming store options
        Map<String, String> streamingStoreOptions = extractStreamingStoreOptions(
            options, streamingStore.toString());
        
        // Create new context with streaming store options
        DynamicTableFactory.Context newContext = createStreamingStoreContext(
            context, streamingStoreOptions);
        
        // Delegate to streaming store's source factory
        return sourceFactory.createDynamicTableSource(newContext);
    }
    
    // Normal Paimon table source creation
    return createPaimonTableSource(context);
}
```

**Key Points**:
- Detects `streaming-store` property in table options
- Discovers the streaming store's `DynamicTableSourceFactory` via SPI
- Creates a new context with transformed streaming store options
- Delegates source creation to the streaming store's factory
- This allows direct reuse of `fluss-connector-flink` source implementation

##### 2.2.3 Enhanced `AbstractFlinkTableFactory.createDynamicTableSink()` Method

The `AbstractFlinkTableFactory.createDynamicTableSink()` method follows the same pattern:

```java
public DynamicTableSink createDynamicTableSink(Context context) {
    Map<String, String> options = context.getCatalogTable().getOptions();
    CoreOptions.StreamingStore streamingStore = 
        Options.fromMap(options).get(STREAMING_STORE);
    
    if (streamingStore != null) {
        // Discover streaming store's DynamicTableSinkFactory via SPI
        DynamicTableSinkFactory sinkFactory = FactoryUtil.discoverFactory(
            context.getClassLoader(), 
            DynamicTableSinkFactory.class, 
            streamingStore.toString());
        
        // Extract streaming store options
        Map<String, String> streamingStoreOptions = extractStreamingStoreOptions(
            options, streamingStore.toString());
        
        // Create new context with streaming store options
        DynamicTableFactory.Context newContext = createStreamingStoreContext(
            context, streamingStoreOptions);
        
        // Delegate to streaming store's sink factory
        return sinkFactory.createDynamicTableSink(newContext);
    }
    
    // Normal Paimon table sink creation
    return createPaimonTableSink(context);
}
```

**Key Points**:
- Similar detection and delegation pattern as source creation
- Allows direct reuse of `fluss-connector-flink` sink implementation
- Special handling for `INSERT OVERWRITE`: When the operation is `INSERT OVERWRITE`, writes should go directly to Paimon instead of Fluss, as overwrite operations need to replace existing data in Paimon

##### 2.2.4 Write Behavior Details

When `streaming-store=fluss` is set:

1. **Streaming Writes**: All streaming write operations are routed to Fluss via Fluss's Flink sink connector
2. **Batch Writes**: Most batch write operations are routed to Fluss, **except**:
   - **INSERT OVERWRITE**: Goes directly to Paimon to replace existing data, as overwrite semantics require direct Paimon access
3. **Tiering Service**: Fluss's Tiering Service asynchronously writes data from Fluss to Paimon, maintaining:
   - Data consistency
   - Snapshot generation
   - Manifest updates

##### 2.2.5 Read Behavior Details

When `streaming-store=fluss` is set:

1. **Union Read (Default)**: When `batch-scan-streaming-store=true` (default), the read path performs union read:
   - **Historical Data**: Reads from Paimon snapshots (data that has been tiered from Fluss)
   - **Real-Time Data**: Reads from Fluss streaming store (data that has not yet been tiered to Paimon)
   - The union read ideally should only read the latest data from Fluss (data not yet tiered), avoiding duplicate reads
   
2. **Batch Scan Mode**: When `batch-scan-streaming-store=false`, batch scan operations only read from Paimon snapshots:
   - Bypasses Fluss streaming store entirely
   - Provides better query performance for OLAP engines (e.g., StarRocks, Hologress)
   - Useful when Union Read performance is suboptimal, especially for primary key tables where merge and deduplication operations are required

3. **Data Merging**: When union read is enabled:
   - For primary key tables: Merges based on primary key, with Fluss data taking precedence for recent records
   - For append-only tables: Concatenates data from both sources

4. **Consistency**: The read path ensures:
   - No duplicate records
   - Correct ordering (historical data first, then real-time data)
   - Schema evolution compatibility

#### 2.3 Spark Read/Write Path Changes

##### 2.3.1 Introduce `StreamingStoreSparkTableFactory` Interface

A new interface is introduced to allow streaming stores to provide Spark table implementations:

```java
/**
 * Factory interface for creating Spark table implementations for data read/write operations
 * with streaming stores.
 *
 * <p>This factory is used to create Spark table instances for reading from and writing to
 * external streaming stores (e.g., Fluss). When a Paimon table is configured with
 * `streaming-store`, the `SparkCatalog.loadTable()` method discovers the corresponding
 * factory implementation via SPI and delegates table creation to it.
 *
 * <p>The returned {@code Table} instance is used solely for data read/write operations,
 * not for table management (e.g., creating or dropping tables). Table management is
 * handled by the {@code StreamingStore} interface in the catalog layer.
 *
 * <p>Implementations of this interface should be registered via Java's ServiceLoader mechanism
 * using the {@code org.apache.paimon.factories.Factory} service identifier. The factory identifier
 * should match the value specified in the {@code streaming-store} table property (e.g., "fluss").
 *
 * <p>This approach allows Paimon to directly reuse streaming store's Spark connector
 * implementations (e.g., `fluss-connector-spark`) without code duplication, minimizing
 * changes to Paimon's core codebase.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // When streaming-store=fluss is set, the FlussStreamingStoreSparkTableFactory is discovered
 * StreamingStoreSparkTableFactory factory = FactoryUtil.discoverFactory(
 *     classLoader, StreamingStoreSparkTableFactory.class, "fluss");
 * Table sparkTable = factory.create(identifier, properties);
 * // The returned Table is used for read/write operations only
 * }</pre>
 */
public interface StreamingStoreSparkTableFactory extends Factory {
    
    /**
     * Returns the factory identifier, which should match the streaming store identifier
     * (e.g., "fluss").
     *
     * @return The factory identifier
     */
    String factoryIdentifier();
    
    /**
     * Creates a Spark table instance for data read/write operations with the streaming store.
     *
     * <p>This method is called by {@code SparkCatalog.loadTable()} when a table with
     * `streaming-store` property is loaded. The properties typically contain configuration
     * specific to the streaming store implementation, such as connection endpoints,
     * authentication credentials, etc. Options prefixed with the streaming store name
     * (e.g., "fluss.") are passed to this method after removing the prefix.
     *
     * <p>The returned {@code Table} instance is used solely for data read/write operations,
     * not for table management. It must implement both {@code SupportsWrite} and
     * {@code SupportsRead} interfaces to support both read and write operations. This is
     * essential for the streaming store integration, as it enables:
     * <ul>
     *   <li>Write operations: Data is written to the streaming store (e.g., Fluss) instead
     *       of directly to Paimon</li>
     *   <li>Read operations: Data is read from both the streaming store and Paimon using
     *       Union Read (when enabled via {@code batch-scan-streaming-store=true})</li>
     * </ul>
     *
     * <p>Note: Table management operations (e.g., creating or dropping tables) are handled
     * by the {@code StreamingStore} interface in the catalog layer, not by the returned
     * {@code Table} instance.
     *
     * @param identifier The table identifier (database and table name)
     * @param properties Configuration properties for the streaming store table
     * @return A Spark table instance that implements both {@code SupportsWrite} and
     *         {@code SupportsRead} interfaces for data read/write operations only
     * @throws RuntimeException if the table creation fails
     */
    org.apache.spark.sql.connector.catalog.Table create(
        org.apache.spark.sql.connector.catalog.Identifier identifier, 
        Map<String, String> properties);
}
```

**Key Points**:
- Follows the same factory pattern as `StreamingStoreFactory`
- Allows streaming store implementations to provide Spark-specific table implementations
- Registered via SPI using the streaming store identifier
- Enables direct reuse of streaming store's Spark connector implementations (e.g., `fluss-connector-spark`)
- The returned `Table` instance is used solely for data read/write operations, not for table management
- The returned `Table` instance must implement both `SupportsWrite` and `SupportsRead` interfaces to support read and write operations
- Table management operations (e.g., creating or dropping tables) are handled by the `StreamingStore` interface in the catalog layer

##### 2.3.2 Enhanced `SparkCatalog.loadTable()` Method

The `SparkCatalog.loadTable()` method is enhanced to detect and delegate to streaming store's Spark table factory:

```java
public org.apache.spark.sql.connector.catalog.Table loadTable(Identifier ident) 
        throws NoSuchTableException {
    // Load table from Paimon catalog to get schema and options
    org.apache.paimon.table.Table paimonTable = 
        catalog.getTable(toIdentifier(ident, catalogName));
    
    // Check if this table uses a streaming store
    CoreOptions.StreamingStore streamingStore = 
        CoreOptions.fromMap(paimonTable.options()).get(STREAMING_STORE);
    
    if (streamingStore != null) {
        // Discover streaming store's SparkTableFactory via SPI
        StreamingStoreSparkTableFactory sparkTableFactory = 
            FactoryUtil.discoverFactory(
                classLoader, 
                StreamingStoreSparkTableFactory.class, 
                streamingStore.toString());
        
        // Extract streaming store options
        Map<String, String> streamingStoreOptions = extractStreamingStoreOptions(
            paimonTable.options(), streamingStore.toString());
        
        // Delegate to streaming store's Spark table factory
        return sparkTableFactory.create(
            ident, 
            streamingStoreOptions);
    }
    
    // Normal Paimon table path
    return new SparkTable(copyWithSQLConf(paimonTable, catalogName, 
        toIdentifier(ident, catalogName), Collections.emptyMap()));
}
```

**Key Points**:
- Detects `streaming-store` property in table options
- Discovers the streaming store's `StreamingStoreSparkTableFactory` via SPI
- Extracts and transforms streaming store options
- Delegates table creation to the streaming store's factory
- Returns the streaming store's Spark table directly

##### 2.3.3 Write and Read Behavior

The write and read behavior for Spark follows the same principles as Flink:
- Writes are routed to Fluss (except INSERT OVERWRITE which goes to Paimon)
- Reads use union read by default, with option to disable via `batch-scan-streaming-store=false`

#### 2.4 Benefits of Hook-Based Approach

1. **Minimal Code Changes**: Only detection and delegation logic is added to Paimon, keeping the core codebase clean
2. **Code Reuse**: Directly reuses existing streaming store connector implementations without duplication
3. **Maintainability**: Streaming store-specific logic stays in streaming store connectors, not in Paimon
4. **Extensibility**: Easy to add support for new streaming stores by implementing the factory interfaces
5. **Transparency**: Users continue to use Paimon APIs without knowing about the delegation
6. **Performance**: Leverages optimized streaming store connectors directly

### 4. Changes in Catalog

The catalog implementation is enhanced to support upgrading existing Paimon tables to use streaming stores. The main changes are in `FileSystemCatalog.alterTableImpl()` and `FileSystemCatalog.createTableImpl()` methods.

#### 4.1 Enhanced `alterTableImpl()` Method

The `FileSystemCatalog.alterTableImpl()` method is enhanced to detect and handle streaming store upgrades:

1. **Detect Streaming Store Upgrade**:
   - When processing schema changes, the method checks if `streaming-store` property is being set to a streaming store value (e.g., `'fluss'`)
   - It compares the current `streaming-store` value with the new value to determine if this is an upgrade operation
   - The detection is done by iterating through `SchemaChange.SetOption` changes and matching against `STREAMING_STORE.key()`

2. **Extract Streaming Store Options**:
   - All options prefixed with the streaming store name (e.g., `fluss.*`) are extracted from the schema changes
   - Existing streaming store options from the current table schema are also collected
   - The prefix is removed (e.g., `fluss.bootstrap.servers` becomes `bootstrap.servers`) before passing to the streaming store implementation

3. **Factory Discovery and Streaming Store Creation**:
   - The `StreamingStoreFactory` is discovered via SPI using `FactoryUtil.discoverFactory()` with the streaming store identifier (e.g., `"fluss"`)
   - The factory's `createStreamingStore()` method is called with the extracted options to create a `StreamingStore` instance
   - This follows the pluggable factory pattern, allowing different streaming store implementations to be registered

4. **Coordinate with Streaming Store Server**:
   - The `StreamingStore.createTable()` method is called to create a corresponding table in the streaming store
   - The table is created with the same identifier and schema as the Paimon table to ensure compatibility
   - The `ignoreIfExists` parameter is set to `true` to make the operation idempotent

5. **Schema Commit Coordination**:
   - After the streaming store table is created, the schema changes need to be committed to the Paimon table
   - The implementation supports two coordination models:
     - **Synchronous Model**: Schema changes are committed to Paimon immediately after streaming store table creation
     - **Callback Model**: The streaming store server calls back to Paimon to commit schema changes, identified by a special marker (`fluss.callback`)
   - The callback model allows the streaming store server to perform additional validation or processing before committing

6. **Handle Streaming Store Callbacks**:
   - When a callback is received from the streaming store server, it includes a special marker option (e.g., `fluss.callback=true`)
   - The catalog detects this marker and processes the callback differently:
     - The marker is removed from the schema changes before committing
     - Schema changes are committed directly without re-notifying the streaming store server
     - This prevents infinite callback loops

7. **Normal Schema Update Path**:
   - If no streaming store upgrade is detected, the method follows the normal schema update path
   - Schema changes are committed directly to Paimon using `SchemaManager.commitChanges()`
   - This ensures backward compatibility with existing schema evolution operations

#### 4.2 Enhanced `createTableImpl()` Method

The `FileSystemCatalog.createTableImpl()` method is enhanced to support creating tables with streaming store from the beginning:

1. **Check for Streaming Store Configuration**:
   - When creating a new table, the method checks if `streaming-store` property is set in the table schema
   - If set, it extracts the streaming store options and creates the corresponding table in the streaming store

2. **Create Streaming Store Table**:
   - Uses the same factory discovery mechanism as `alterTableImpl()` to create a `StreamingStore` instance
   - Calls `StreamingStore.createTable()` to create the table in the streaming store
   - The table is created with the same identifier and schema as the Paimon table

3. **Create Paimon Table**:
   - After the streaming store table is created, the Paimon table is created using the normal `SchemaManager.createTable()` method
   - Both tables are created atomically within a lock to ensure consistency

#### 4.3 Error Handling and Rollback

- If streaming store table creation fails, the Paimon table creation is also aborted
- Errors from the streaming store are propagated to the user with clear error messages
- The implementation ensures that partial state (e.g., streaming store table created but Paimon table not created) is avoided

#### 4.4 Locking and Concurrency

- All streaming store operations are performed within the catalog's locking mechanism
- The `runWithLock()` method ensures that concurrent schema changes are serialized
- This prevents race conditions when multiple operations try to upgrade the same table simultaneously

### 5. Upgrade Workflow

The recommended upgrade workflow is:

1. **Stop Write Pipelines**: Users must stop all write pipelines for the target table to ensure no writes go directly to Paimon after upgrade.

   **Note**: This is currently a soft requirement. Future enhancements may add permission-based hard restrictions to prevent direct Paimon writes after upgrade.

2. **Execute DDL**: Run the `ALTER TABLE` statement to set `streaming-store=fluss` and required Fluss properties.

3. **Restart Write Pipelines**: Restart write pipelines with stateless restart (no checkpoint recovery), as the topology changes.

4. **Read Pipelines**: Read pipelines continue to work without changes, automatically using union read.

### 6. Permission and Security

**Current State**:
- Users need to apply for Fluss-side permissions before upgrading tables.
- No automatic permission synchronization between Paimon and Fluss.

**Future Enhancement**:
- Integration with DLF (Data Lake Format) permissions to automatically sync permissions between Paimon and Fluss.
- This will eliminate the need for separate Fluss permission applications.

## Compatibility, Deprecation, and Migration Plan

### Backward Compatibility

- **Existing Tables**: Tables without `streaming-store` property continue to work exactly as before. No breaking changes.

- **Read APIs**: All existing read APIs continue to work. Union read is transparent to application code.

- **Write APIs**: All existing write APIs continue to work. Write routing to Fluss is transparent to application code.

- **Schema Evolution**: Existing schema evolution features (add/drop columns, change types) continue to work with Fluss tables.

### Migration Path

1. **Upgrade Existing Tables**: Users can upgrade existing tables using the `ALTER TABLE` DDL without data migration.

2. **Gradual Migration**: Users can upgrade tables one at a time, allowing for gradual adoption.

3. **Rollback**: While not explicitly supported in the initial implementation, the design allows for potential rollback by:
   - Removing `streaming-store` property (future enhancement)
   - Stopping Fluss writes
   - Resuming direct Paimon writes

### Deprecation

No existing features are deprecated by this PIP.

### Breaking Changes

None. This is a purely additive feature.

### Timeline

- **Phase 1 (Initial Implementation)**: 
  - Support upgrading tables to Fluss lake tables
  - Basic union read functionality
  - Write routing to Fluss
  
- **Phase 2 (Future Enhancements)**:
  - Permission integration with DLF
  - Hard restrictions on direct Paimon writes after upgrade
  - Rollback support
  - Enhanced monitoring and metrics

## Test Plan

### Unit Tests

1. **Schema Evolution Tests**:
   - Test `ALTER TABLE` with `streaming-store=fluss`
   - Test extraction of `fluss.*` properties
   - Test Fluss server notification logic
   - Test schema commit after Fluss callback

2. **Write Path Tests**:
   - Test write routing to Fluss when `streaming-store=fluss`
   - Test that writes don't go directly to Paimon after upgrade
   - Test stateless restart of write jobs

3. **Read Path Tests**:
   - Test union read combining Paimon and Fluss data (when `batch-scan-streaming-store=true`)
   - Test batch scan mode reading only from Paimon (when `batch-scan-streaming-store=false`)
   - Test data merging logic for primary key tables
   - Test data concatenation for append-only tables
   - Test schema evolution compatibility in union read
   - Test that `batch-scan-streaming-store=false` correctly bypasses Fluss for OLAP engines

### Integration Tests

1. **End-to-End Upgrade Test**:
   - Create a Paimon table with existing data
   - Execute upgrade DDL
   - Verify Fluss table creation
   - Write new data and verify it goes to Fluss
   - Read data and verify union read returns both historical and real-time data

2. **Write Pipeline Test**:
   - Start write pipeline for regular Paimon table
   - Stop pipeline
   - Upgrade table to Fluss
   - Restart pipeline (stateless)
   - Verify writes go to Fluss
   - Verify Tiering Service writes to Paimon

3. **Read Pipeline Test**:
   - Create table with historical data
   - Upgrade to Fluss
   - Write new data to Fluss
   - Read from table and verify union read works correctly (default behavior)
   - Verify no duplicate records
   - Verify correct ordering
   - Test with `batch-scan-streaming-store=false` to verify only Paimon data is read

4. **Schema Evolution Test**:
   - Upgrade table to Fluss
   - Add new column via `ALTER TABLE`
   - Verify schema changes propagate to both Paimon and Fluss
   - Write data with new column
   - Read data and verify schema evolution works

5. **Failure Recovery Test**:
   - Test behavior when Fluss server is unavailable during upgrade
   - Test behavior when Fluss server fails during write
   - Test read behavior when Fluss is temporarily unavailable (should fall back to Paimon-only read)

### Performance Tests

1. **Write Performance**:
   - Compare write latency: direct Paimon writes vs. Fluss writes
   - Measure Tiering Service overhead

2. **Read Performance**:
   - Measure union read performance vs. Paimon-only read
   - Compare performance with `batch-scan-streaming-store=true` vs. `false`
   - Test with various data sizes (historical vs. real-time)
   - Measure query latency impact for OLAP engines (StarRocks, Hologress)
   - Benchmark Union Read performance in StarRocks to validate the need for `batch-scan-streaming-store=false`

3. **Scalability Tests**:
   - Test with large historical datasets
   - Test with high write throughput
   - Test with many concurrent readers

### System Tests

1. **Multi-Table Upgrade**: Test upgrading multiple tables in the same database.

2. **Concurrent Operations**: Test concurrent reads and writes during and after upgrade.

3. **Long-Running Jobs**: Test that long-running read jobs continue to work after table upgrade.

## Rejected Alternatives

### Alternative 1: Separate Fluss Table Creation

**Proposal**: Require users to manually create Fluss tables separately, then link them to Paimon tables.

**Rejection Reason**: 
- Adds complexity for users
- Requires manual coordination between two systems
- Higher chance of configuration mismatches
- Doesn't provide seamless upgrade experience

### Alternative 2: Data Migration Approach

**Proposal**: Copy all existing Paimon data to Fluss during upgrade.

**Rejection Reason**:
- Expensive for large tables
- Requires significant downtime
- Unnecessary since union read can access both sources
- Defeats the purpose of keeping historical data in Paimon

### Alternative 3: Dual Write Approach

**Proposal**: Write to both Paimon and Fluss simultaneously during transition period.

**Rejection Reason**:
- Doubles write overhead
- Requires complex coordination to avoid duplicates
- Doesn't solve the latency issue (Paimon writes still have minute-level latency)
- More complex failure handling

### Alternative 4: Read-Only Fluss Integration

**Proposal**: Only enable Fluss for reads, keep writes going directly to Paimon.

**Rejection Reason**:
- Doesn't solve the data freshness problem
- Real-time data in Fluss would be read-only
- Doesn't provide the second-level latency benefit for writes

### Alternative 5: Separate API for Fluss Tables

**Proposal**: Create separate read/write APIs specifically for Fluss tables.

**Rejection Reason**:
- Breaks backward compatibility
- Requires application code changes
- Doesn't provide transparent upgrade path
- Increases API surface area and maintenance burden

### Alternative 6: Automatic Tiering Without User Control

**Proposal**: Automatically tier all tables to Fluss without user opt-in.

**Rejection Reason**:
- Not all tables need second-level latency
- Adds unnecessary overhead for batch-only workloads
- Users should have control over when to enable this feature
- May have cost implications (Fluss infrastructure)

The chosen approach (opt-in upgrade with transparent read/write APIs) provides the best balance of:
- User control
- Backward compatibility
- Seamless upgrade experience
- Performance optimization (only enable when needed)

