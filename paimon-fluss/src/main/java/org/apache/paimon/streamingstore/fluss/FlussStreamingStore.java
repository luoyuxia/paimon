package org.apache.paimon.streamingstore.fluss;

import org.apache.fluss.client.Connection;
import org.apache.fluss.client.ConnectionFactory;
import org.apache.fluss.client.admin.Admin;
import org.apache.fluss.config.Configuration;
import org.apache.fluss.metadata.TableDescriptor;
import org.apache.fluss.metadata.TablePath;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.schema.Schema;
import org.apache.paimon.schema.SchemaChange;
import org.apache.paimon.streamingstore.StreamingStore;
import org.apache.paimon.table.BucketMode;
import org.apache.paimon.table.FileStoreTable;
import org.apache.paimon.table.Table;
import org.apache.paimon.types.DataType;
import org.apache.paimon.types.DataTypes;
import org.apache.paimon.utils.ExceptionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.fluss.metadata.TableDescriptor.BUCKET_COLUMN_NAME;
import static org.apache.fluss.metadata.TableDescriptor.OFFSET_COLUMN_NAME;
import static org.apache.fluss.metadata.TableDescriptor.TIMESTAMP_COLUMN_NAME;
import static org.apache.paimon.streamingstore.fluss.utils.FlussConversions.toFlussTableDescriptor;

public class FlussStreamingStore implements StreamingStore, AutoCloseable {

    public static final LinkedHashMap<String, DataType> SYSTEM_COLUMNS = new LinkedHashMap<>();

    static {
        // We need __bucket system column to filter out the given bucket
        // for paimon bucket-unaware append only table.
        // It's not required for paimon bucket-aware table like primary key table
        // and bucket-aware append only table, but we always add the system column
        // for consistent behavior
        SYSTEM_COLUMNS.put(BUCKET_COLUMN_NAME, DataTypes.INT());
        SYSTEM_COLUMNS.put(OFFSET_COLUMN_NAME, DataTypes.BIGINT());
        SYSTEM_COLUMNS.put(TIMESTAMP_COLUMN_NAME, DataTypes.TIMESTAMP_WITH_LOCAL_TIME_ZONE());
    }

    private final Connection connection;
    private final Admin admin;


    public FlussStreamingStore(Map<String, String> options) {
        connection = ConnectionFactory.createConnection(Configuration.fromMap(options));
        admin = connection.getAdmin();
    }


    @Override
    public List<SchemaChange> createTable(Identifier identifier, Table table) {
        FileStoreTable fileStoreTable = (FileStoreTable) table;
        if (fileStoreTable.bucketMode() != BucketMode.BUCKET_UNAWARE || fileStoreTable.bucketMode() != BucketMode.HASH_FIXED) {
            throw new IllegalArgumentException("Unsupported bucket mode: " + fileStoreTable.bucketMode() + ". Only bucket unaware and hash fixed modes are supported.");
        }

        TableDescriptor flussTableDescriptor = toFlussTableDescriptor(table);
        try {
            admin.createTable(
                    toTablePath(identifier),
                    flussTableDescriptor,
                    true
            ).get();
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Fail to create table %s in %s", identifier, FlussStreamingStoreFactory.IDENTIFIER),
                    ExceptionUtils.stripCompletionException(e));
        }

        List<SchemaChange> changes = new ArrayList<>(SYSTEM_COLUMNS.size());
        for (Map.Entry<String, DataType> systemColumn : SYSTEM_COLUMNS.entrySet()) {
            changes.add(
                    SchemaChange.addColumn(systemColumn.getKey(), systemColumn.getValue())
            );
        }
        return changes;
    }

    @Override
    public void dropPartitions(Identifier identifier, List<Map<String, String>> partitions) throws Catalog.TableNotExistException {
        // todo:
    }

    @Override
    public void dropTable(Identifier identifier) {
        // always ignore if not exists
        admin.dropTable(toTablePath(identifier), true);
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

    private TablePath toTablePath(Identifier identifier) {
        return TablePath.of(identifier.getDatabaseName(), identifier.getTableName());
    }
}
