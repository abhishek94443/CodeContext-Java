package com.codecontext.core.security.inventory;

import java.util.Objects;

/**
 * Structural relationship between cryptographic assets, components, or source callers.
 */
public record CryptoAssetRelationship(
        String sourceId,
        String targetId,
        String relationshipType
) {
    public CryptoAssetRelationship {
        Objects.requireNonNull(sourceId, "sourceId cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");
        Objects.requireNonNull(relationshipType, "relationshipType cannot be null");
    }
}

