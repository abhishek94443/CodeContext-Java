package com.codecontext.cli.export.security;

import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.pqc.PqcAssessmentReport;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        Collection<RuleMetadata> activeRules,
        Optional<PqcAssessmentReport> pqcReport
) {
    public SecurityReportPayload {
        Objects.requireNonNull(repositoryName, "repositoryName cannot be null");
        Objects.requireNonNull(repositoryPath, "repositoryPath cannot be null");
        scanTimestamp = scanTimestamp == null ? Instant.now() : scanTimestamp;
        scannerVersion = scannerVersion == null ? "2.0.0" : scannerVersion;
        Objects.requireNonNull(analysisResult, "analysisResult cannot be null");
        Objects.requireNonNull(cryptoInventory, "cryptoInventory cannot be null");
        activeRules = activeRules == null ? List.of() : List.copyOf(activeRules);
        pqcReport = pqcReport == null ? Optional.empty() : pqcReport;
    }

    public SecurityReportPayload(
            String repositoryName,
            Path repositoryPath,
            Instant scanTimestamp,
            String scannerVersion,
            SecurityAnalysisResult analysisResult,
            CryptoInventory cryptoInventory,
            Collection<RuleMetadata> activeRules
    ) {
        this(repositoryName, repositoryPath, scanTimestamp, scannerVersion, analysisResult, cryptoInventory, activeRules, Optional.empty());
    }
}
