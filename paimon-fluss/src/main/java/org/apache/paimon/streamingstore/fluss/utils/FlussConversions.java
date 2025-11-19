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

package org.apache.paimon.streamingstore.fluss.utils;

import org.apache.fluss.config.ConfigOptions;
import org.apache.paimon.table.BucketMode;
import org.apache.paimon.table.FileStoreTable;
import org.apache.paimon.table.Table;
import org.apache.paimon.types.DataField;
import org.apache.paimon.types.RowType;

import org.apache.fluss.metadata.Schema;
import org.apache.fluss.metadata.TableDescriptor;

import java.util.List;
import java.util.Map;

import static org.apache.fluss.config.FlussConfigUtils.isTableStorageConfig;
import static org.apache.paimon.options.OptionsUtils.convertToPropertiesPrefixed;

public class FlussConversions {

    private static final String BUCKET_NUM_KEY = "fluss.bucket.num";

    public static TableDescriptor toFlussTableDescriptor(Table table) {
        RowType rowType = table.rowType();
        List<String> primaryKeys = table.primaryKeys();
        List<String> partitionKeys = table.partitionKeys();
        TableDescriptor.Builder tableDescriptorBuilder = TableDescriptor.builder();

        // convert schema
        Schema.Builder schemaBuilder = Schema.newBuilder();
        for (DataField field : rowType.getFields()) {
            schemaBuilder
                    .column(
                            field.name(),
                            field.type().accept(PaimonDataTypeToFlussDataType.INSTANCE))
                    .withComment(field.description());
        }

        if (!primaryKeys.isEmpty()) {
            schemaBuilder.primaryKey(primaryKeys);
        }
        tableDescriptorBuilder.schema(schemaBuilder.build());

        if (!partitionKeys.isEmpty()) {
            tableDescriptorBuilder.partitionedBy(partitionKeys);
        }

        // set distribute by
        Map<String, String> options = table.options();
        FileStoreTable fileStoreTable = (FileStoreTable) table;
        if (fileStoreTable.bucketMode() == BucketMode.BUCKET_UNAWARE) {
            String bucketNum = options.get(BUCKET_NUM_KEY);
            if (bucketNum != null) {
                tableDescriptorBuilder.distributedBy(Integer.parseInt(bucketNum));
            }
        } else if (fileStoreTable.bucketMode() == BucketMode.HASH_FIXED) {
            tableDescriptorBuilder.distributedBy(
                    fileStoreTable.bucketSpec().getNumBuckets(),
                    fileStoreTable.bucketSpec().getBucketKeys());
        }

        // get fluss related options
        Map<String, String> flussOptions = convertToPropertiesPrefixed(options, "fluss");
        for (Map.Entry<String, String> flussOption : flussOptions.entrySet()) {
            if (isTableStorageConfig(flussOption.getKey())) {
                tableDescriptorBuilder.property(flussOption.getKey(), flussOption.getValue());
            }
            options.remove(flussOption.getKey());
        }

        tableDescriptorBuilder.property(ConfigOptions.TABLE_DATALAKE_ENABLED.key(), "true");
        tableDescriptorBuilder.property(ConfigOptions.TABLE_DATALAKE_FORMAT.key(), "paimon");

        // then, convert remain options to fluss custom option with paimon. as prefix
        for (Map.Entry<String, String> option : table.options().entrySet()) {
            tableDescriptorBuilder.customProperty("paimon." + option.getKey(), option.getValue());
        }

        return tableDescriptorBuilder.build();
    }
}
