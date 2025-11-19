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

package org.apache.paimon.streamingstore;

import org.apache.paimon.factories.Factory;

import java.util.Map;

/**
 * Factory interface for creating {@link StreamingStore} instances.
 *
 * <p>This factory is used to create streaming store instances for managing tables in external
 * streaming stores (e.g., Fluss). When a Paimon table is upgraded to use a streaming store by
 * setting the {@code streaming-store} table property, the corresponding factory implementation is
 * discovered via SPI and used to create a streaming store instance for managing the table in the
 * streaming store.
 */
public interface StreamingStoreFactory extends Factory, AutoCloseable {

    /**
     * Creates a {@link StreamingStore} instance with the given options.
     *
     * <p>The options typically contain configuration specific to the streaming store
     * implementation, such as connection endpoints, authentication credentials, etc. Options
     * prefixed with the streaming store name (e.g., "fluss.") are typically passed to this method
     * after removing the prefix.
     *
     * @param options Configuration options for the streaming store
     * @return A new {@link StreamingStore} instance
     */
    StreamingStore createStreamingStore(Map<String, String> options);
}
