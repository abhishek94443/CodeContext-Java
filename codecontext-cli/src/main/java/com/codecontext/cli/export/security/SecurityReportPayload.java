package com.codecontext.cli.export.security;

import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.inventory.CryptoInventory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Immutable data payload dedicated exclusively to the Standalone Security & Cryptographic Dashboard.
 * Completely decoupled from architectural graph visualization models.
 */
public record SecurityReportPayload(
        String repositoryName,
        Path repositoryPath,
        Instant scanTimestamp,
        String scannerVersion,
        SecurityAnalysisResult analysisResult,
        CryptoInventory cryptoInventory,
        Collection<RuleMetadata> activeRules
) {
    public SecurityReportPayload {
        Objects.requireNonNull(repositoryName, "repositoryName cannot be null");
        Objects.requireNonNull(repositoryPath, "repositoryPath cannot be null");
        scanTimestamp = scanTimestamp == null ? Instant.now() : scanTimestamp;
        scannerVersion = scannerVersion == null ? "1.0.0" : scannerVersion;
        Objects.requireNonNull(analysisResult, "analysisResult cannot be null");
        Objects.requireNonNull(cryptoInventory, "cryptoInventory cannot be null");
        activeRules = activeRules == null ? List.of() : List.copyOf(activeRules);
    }
}

