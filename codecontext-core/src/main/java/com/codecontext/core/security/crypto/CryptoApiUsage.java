package com.codecontext.core.security.crypto;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.model.SourceRange;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable record of an observed cryptographic API usage in Java source code.
 * Maintained independently of security findings to support cryptographic inventory and analysis.
 */
public record CryptoApiUsage(
        CryptoApiOwner owner,
        String methodName,
        Path filePath,
        SourceRange location,
        String originalArgumentExpr,
        ExpressionResolution argumentResolution,
        Optional<CryptoAlgorithm> algorithm,
        AnalysisConfidence ownerConfidence,
        Map<String, String> properties
) {
    public CryptoApiUsage {
        Objects.requireNonNull(owner, "owner cannot be null");
        Objects.requireNonNull(methodName, "methodName cannot be null");
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        originalArgumentExpr = originalArgumentExpr == null ? "" : originalArgumentExpr;
        Objects.requireNonNull(argumentResolution, "argumentResolution cannot be null");
        algorithm = algorithm == null ? Optional.empty() : algorithm;
        ownerConfidence = ownerConfidence == null ? AnalysisConfidence.LOW : ownerConfidence;
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public boolean isOwnerConfirmed() {
        return ownerConfidence == AnalysisConfidence.HIGH;
    }

    public boolean isAlgorithmResolved() {
        return argumentResolution.isResolved();
    }
}

