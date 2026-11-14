package com.codecontext.core.security.pqc;

import com.codecontext.core.security.inventory.CryptoAsset;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Architectural call-graph blast radius for a cryptographic asset.
 * Separates direct upstream callers (1-hop) from transitive-only callers (depth >= 2).
 * Explicitly documents class-level graph approximation semantics.
 */
public record CryptoAssetBlastRadius(
        String assetId,
        String assetName,
        Set<String> directDependentClasses,
        Set<String> transitiveOnlyDependentClasses,
        int totalReachableClasses,
        int maxCallDepth,
        double cumulativeRiskScore,
        List<String> affectedCompilationUnits,
        boolean isSharedUtilityUsage,
        String reachabilitySemanticsNote
) {
    public CryptoAssetBlastRadius {
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(assetName, "assetName cannot be null");
        directDependentClasses = directDependentClasses == null ? Set.of() : Set.copyOf(directDependentClasses);
        transitiveOnlyDependentClasses = transitiveOnlyDependentClasses == null ? Set.of() : Set.copyOf(transitiveOnlyDependentClasses);
        affectedCompilationUnits = affectedCompilationUnits == null ? List.of() : List.copyOf(affectedCompilationUnits);
        reachabilitySemanticsNote = reachabilitySemanticsNote == null ? "" : reachabilitySemanticsNote;
    }

    public int totalDirectDependents() {
        return directDependentClasses.size();
    }

    public int totalTransitiveOnlyDependents() {
        return transitiveOnlyDependentClasses.size();
    }

    public Set<String> transitiveDependentClasses() {
        return transitiveOnlyDependentClasses;
    }

    public static CryptoAssetBlastRadius empty(CryptoAsset asset) {
        return new CryptoAssetBlastRadius(
                asset.assetId(),
                asset.name(),
                Set.of(),
                Set.of(),
                0,
                0,
                0.0,
                List.of(),
                false,
                "No graph dependency data available."
        );
    }
}
