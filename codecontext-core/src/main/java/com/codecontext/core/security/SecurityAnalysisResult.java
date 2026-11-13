package com.codecontext.core.security;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.security.crypto.CryptoApiUsage;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Immutable outcome of a security and cryptographic static analysis run.
 */
public record SecurityAnalysisResult(
    List<Finding> findings,
    int filesScanned,
    List<Path> failedFiles,
    List<String> analysisErrors,
    long durationMs,
    Collection<RuleMetadata> activeRules,
    List<CryptoApiUsage> cryptoUsages
) {
    public SecurityAnalysisResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        failedFiles = failedFiles == null ? List.of() : List.copyOf(failedFiles);
        analysisErrors = analysisErrors == null ? List.of() : List.copyOf(analysisErrors);
        activeRules = activeRules == null ? List.of() : List.copyOf(activeRules);
        cryptoUsages = cryptoUsages == null ? List.of() : List.copyOf(cryptoUsages);
    }

    public SecurityAnalysisResult(
        List<Finding> findings,
        int filesScanned,
        List<Path> failedFiles,
        List<String> analysisErrors,
        long durationMs,
        Collection<RuleMetadata> activeRules
    ) {
        this(findings, filesScanned, failedFiles, analysisErrors, durationMs, activeRules, List.of());
    }

    public SecurityAnalysisResult(
        List<Finding> findings,
        int filesScanned,
        List<Path> failedFiles,
        long durationMs,
        Collection<RuleMetadata> activeRules
    ) {
        this(findings, filesScanned, failedFiles, List.of(), durationMs, activeRules, List.of());
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    public int totalFindings() {
        return findings.size();
    }

    public boolean hasErrors() {
        return !failedFiles.isEmpty() || !analysisErrors.isEmpty();
    }

    public int unresolvedUsagesCount() {
        return (int) cryptoUsages.stream()
                .filter(u -> !u.argumentResolution().isResolved() || !u.isOwnerConfirmed())
                .count();
    }
}
