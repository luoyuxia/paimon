package org.apache.paimon.streamingstore;

import com.sun.org.apache.xml.internal.resolver.CatalogException;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.schema.Schema;
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
 *
 */
public interface StreamingStore {

    /**
     * Creates a table in the streaming store with the specified identifier and schema.
     *
     * <p>This method is called when a Paimon table is upgraded to use a streaming store. The
     * created table should have the same schema as the Paimon table to ensure data compatibility.
     *
     * @param identifier The table identifier (database and table name)
     * @param table The table to create
     */
    List<SchemaChange> createTable(Identifier identifier, Table table, );


    void dropPartitions(Identifier identifier, List<Map<String, String>> partitions) throws Catalog.TableNotExistException;

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
