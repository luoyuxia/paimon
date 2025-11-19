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

package org.apache.paimon.streamingstore.fluss;

import org.apache.paimon.streamingstore.StreamingStore;
import org.apache.paimon.streamingstore.StreamingStoreFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FlussStreamingStoreFactory implements StreamingStoreFactory {

    public static final String IDENTIFIER = "fluss";

    private static final String BOOTSTRAP_SERVERS_KEY = "bootstrap.servers";

    private final Map<String, FlussStreamingStore> stores;

    public FlussStreamingStoreFactory() {
        stores = new ConcurrentHashMap<>();
    }

    @Override
    public StreamingStore createStreamingStore(Map<String, String> options) {
        String bootstrapServers = options.get(BOOTSTRAP_SERVERS_KEY);
        if (bootstrapServers == null) {
            throw new IllegalArgumentException(
                    "Missing required option " + BOOTSTRAP_SERVERS_KEY + " for " + IDENTIFIER);
        }
        return stores.computeIfAbsent(
                bootstrapServers, (_bootstrapServers) -> new FlussStreamingStore(options));
    }

    @Override
    public void close() throws Exception {
        for (FlussStreamingStore store : stores.values()) {
            store.close();
        }
    }

    @Override
    public String identifier() {
        return IDENTIFIER;
    }
}
