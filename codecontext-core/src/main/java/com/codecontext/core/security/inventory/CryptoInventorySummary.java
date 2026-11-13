package com.codecontext.core.security.inventory;

import java.util.Collections;
import java.util.Map;

/**
 * High-level aggregated statistics of a cryptographic inventory.
 */
public record CryptoInventorySummary(
        int totalObservations,
        int uniqueAssets,
        int resolvedAssets,
        int unresolvedObservations,
        int filesWithCrypto,
        Map<String, Integer> familyCounts,
        Map<CryptoPrimitiveType, Integer> primitiveCounts
) {
    public CryptoInventorySummary {
        familyCounts = familyCounts == null ? Map.of() : Collections.unmodifiableMap(familyCounts);
        primitiveCounts = primitiveCounts == null ? Map.of() : Collections.unmodifiableMap(primitiveCounts);
    }
}

