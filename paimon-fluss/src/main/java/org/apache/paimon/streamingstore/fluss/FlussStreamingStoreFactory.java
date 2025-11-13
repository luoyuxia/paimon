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
            throw new IllegalArgumentException("Missing required option " + BOOTSTRAP_SERVERS_KEY + " for " + IDENTIFIER);
        }
        return stores.computeIfAbsent(
                bootstrapServers, (_bootstrapServers) -> new FlussStreamingStore(options)
        );
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
