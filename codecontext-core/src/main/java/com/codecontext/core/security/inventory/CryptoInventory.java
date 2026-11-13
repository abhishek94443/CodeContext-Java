package com.codecontext.core.security.inventory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable canonical inventory containing all discovered and normalized cryptographic assets.
 */
public record CryptoInventory(
        List<CryptoAsset> assets,
        List<CryptoAssetRelationship> relationships,
        CryptoInventorySummary summary
) {
    public CryptoInventory {
        assets = assets == null ? List.of() : Collections.unmodifiableList(assets);
        relationships = relationships == null ? List.of() : Collections.unmodifiableList(relationships);
        Objects.requireNonNull(summary, "summary cannot be null");
    }

    public static CryptoInventory empty() {
        return new CryptoInventory(
                List.of(),
                List.of(),
                new CryptoInventorySummary(0, 0, 0, 0, 0, Map.of(), Map.of())
        );
    }

    public Optional<CryptoAsset> findAsset(String assetId) {
        if (assetId == null) return Optional.empty();
        return assets.stream()
                .filter(a -> a.assetId().equals(assetId))
                .findFirst();
    }

    public List<CryptoAsset> findByFamily(String family) {
        if (family == null) return List.of();
        return assets.stream()
                .filter(a -> a.algorithmFamily().equalsIgnoreCase(family))
                .toList();
    }
}

