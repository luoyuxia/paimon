package org.apache.paimon.spark;

import org.apache.paimon.factories.Factory;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.Table;

import java.util.Map;

/**
 * Factory interface for creating Spark table implementations for data read/write operations
 * with streaming stores.
 *
 * <p>This factory is used to create Spark table instances for reading from and writing to
 * external streaming stores (e.g., Fluss). When a Paimon table is configured with
 * `streaming-store`, the `SparkCatalog.loadTable()` method discovers the corresponding
 * factory implementation via SPI and delegates it to loadTable
 */
public interface StreamingStoreSparkTableFactory extends Factory {
    
    /**
     * Creates a Spark table instance for data read/write operations with the streaming store.
     *
     * <p>This method is called by {@code SparkCatalog.loadTable()} when a table with
     * `streaming-store` property is loaded. The properties typically contain configuration
     * specific to the streaming store implementation, such as connection endpoints,
     * authentication credentials, etc. Options prefixed with the streaming store name
     * (e.g., "fluss.") are passed to this method after removing the prefix.
     *
     * <p>The returned {@code Table} instance is used for data read/write operations.
     * It must implement both {@code SupportsWrite} and {@code SupportsRead} interfaces to support both read and write operations.
     * This is essential for the streaming store integration, as it enables:
     * <ul>
     *   <li>Write operations: Data is written to the streaming store (e.g., Fluss) instead
     *       of directly to Paimon</li>
     *   <li>Read operations: Data is read from both the streaming store and Paimon using
     *       Union Read (when enabled via {@code batch-scan-streaming-store=true})</li>
     * </ul>
     *
     * @param identifier The table identifier (database and table name)
     * @param properties Configuration properties for the streaming store table
     * @return A Spark table instance that implements both {@code SupportsWrite} and
     *         {@code SupportsRead} interfaces for data read/write operations only
     * @throws NoSuchTableException if the table doesn't exist
     */
    Table create(Identifier identifier, Map<String, String> properties) throws NoSuchTableException;
}
