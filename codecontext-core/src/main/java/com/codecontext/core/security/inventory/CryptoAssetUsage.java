package com.codecontext.core.security.inventory;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.crypto.CryptoApiOwner;
import com.codecontext.core.security.crypto.ExpressionResolution;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a single concrete source-code invocation of a cryptographic asset.
 */
public record CryptoAssetUsage(
        String usageId,
        Path filePath,
        SourceRange location,
        Optional<String> enclosingClass,
        Optional<String> enclosingMethod,
        CryptoApiOwner apiOwner,
        String methodName,
        String originalArgument,
        ExpressionResolution argumentResolution,
        AnalysisConfidence confidence
) {
    public CryptoAssetUsage {
        Objects.requireNonNull(usageId, "usageId cannot be null");
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        enclosingClass = enclosingClass == null ? Optional.empty() : enclosingClass;
        enclosingMethod = enclosingMethod == null ? Optional.empty() : enclosingMethod;
        Objects.requireNonNull(apiOwner, "apiOwner cannot be null");
        Objects.requireNonNull(methodName, "methodName cannot be null");
        originalArgument = originalArgument == null ? "" : originalArgument;
        Objects.requireNonNull(argumentResolution, "argumentResolution cannot be null");
        confidence = confidence == null ? AnalysisConfidence.LOW : confidence;
    }
}

