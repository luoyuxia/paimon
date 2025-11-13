package org.apache.paimon.catalog;

import org.apache.paimon.factories.Factory;
import org.apache.paimon.options.Options;

/**
 * Factory interface for creating {@link StreamingStore} instances.
 *
 * <p>This factory is used to create streaming store instances for managing tables in external
 * streaming stores (e.g., Fluss). When a Paimon table is upgraded to use a streaming store by
 * setting the {@code streaming-store} table property, the corresponding factory implementation is
 * discovered via SPI and used to create a streaming store instance for managing the table in the
 * streaming store.
 *
 */
public interface StreamingStoreFactory extends Factory {

    /**
     * Creates a {@link StreamingStore} instance with the given options.
     *
     * <p>The options typically contain configuration specific to the streaming store implementation,
     * such as connection endpoints, authentication credentials, etc. Options prefixed with the
     * streaming store name (e.g., "fluss.") are typically passed to this method after removing the
     * prefix.
     *
     * @param options Configuration options for the streaming store
     * @return A new {@link StreamingStore} instance
     */
    StreamingStore createStreamingStore(Options options);
}
