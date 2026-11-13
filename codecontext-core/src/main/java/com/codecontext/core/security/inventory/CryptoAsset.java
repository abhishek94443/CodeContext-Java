package com.codecontext.core.security.inventory;

import com.codecontext.core.finding.AnalysisConfidence;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Normalized representation of a unique cryptographic asset or algorithm configuration.
 * Groups identical configurations across multiple source call sites while retaining all usage coordinates.
 */
public record CryptoAsset(
        String assetId,
        String name,
        String algorithmFamily,
        CryptoPrimitiveType primitiveType,
        Optional<String> mode,
        Optional<String> padding,
        List<CryptoAssetUsage> usages,
        boolean isResolved,
        AnalysisConfidence confidence
) implements Comparable<CryptoAsset> {

    public CryptoAsset {
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        algorithmFamily = algorithmFamily == null ? name : algorithmFamily;
        Objects.requireNonNull(primitiveType, "primitiveType cannot be null");
        mode = mode == null ? Optional.empty() : mode;
        padding = padding == null ? Optional.empty() : padding;
        usages = usages == null ? List.of() : Collections.unmodifiableList(usages);
        confidence = confidence == null ? AnalysisConfidence.LOW : confidence;
    }

    public int usageCount() {
        return usages.size();
    }

    @Override
    public int compareTo(CryptoAsset other) {
        if (other == null) return 1;
        // 1. Sort resolved before unresolved
        if (this.isResolved != other.isResolved) {
            return this.isResolved ? -1 : 1;
        }
        // 2. Family
        int familyCmp = this.algorithmFamily.compareToIgnoreCase(other.algorithmFamily);
        if (familyCmp != 0) return familyCmp;
        // 3. Name
        int nameCmp = this.name.compareToIgnoreCase(other.name);
        if (nameCmp != 0) return nameCmp;
        // 4. Asset ID
        return this.assetId.compareTo(other.assetId);
    }
}

