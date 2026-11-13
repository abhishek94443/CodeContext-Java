package com.codecontext.core.security.crypto;

import com.codecontext.core.finding.AnalysisConfidence;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of evaluating an AST expression for cryptographic arguments.
 */
public record ExpressionResolution(
        ResolutionStatus status,
        Optional<String> resolvedValue,
        AnalysisConfidence confidence,
        String originalExpression,
        String explanation
) {
    public ExpressionResolution {
        Objects.requireNonNull(status, "status cannot be null");
        resolvedValue = resolvedValue == null ? Optional.empty() : resolvedValue;
        confidence = confidence == null ? AnalysisConfidence.LOW : confidence;
        originalExpression = originalExpression == null ? "" : originalExpression;
        explanation = explanation == null ? "" : explanation;
    }

    public static ExpressionResolution resolved(String value, String originalExpression, String explanation) {
        return new ExpressionResolution(
                ResolutionStatus.RESOLVED,
                Optional.of(value),
                AnalysisConfidence.HIGH,
                originalExpression,
                explanation
        );
    }

    public static ExpressionResolution partiallyResolved(String originalExpression, String explanation) {
        return new ExpressionResolution(
                ResolutionStatus.PARTIALLY_RESOLVED,
                Optional.empty(),
                AnalysisConfidence.LOW,
                originalExpression,
                explanation
        );
    }

    public static ExpressionResolution unknown(String originalExpression, String explanation) {
        return new ExpressionResolution(
                ResolutionStatus.UNKNOWN,
                Optional.empty(),
                AnalysisConfidence.LOW,
                originalExpression,
                explanation
        );
    }

    public static ExpressionResolution ambiguous(String originalExpression, String explanation) {
        return new ExpressionResolution(
                ResolutionStatus.AMBIGUOUS,
                Optional.empty(),
                AnalysisConfidence.LOW,
                originalExpression,
                explanation
        );
    }

    public boolean isResolved() {
        return status == ResolutionStatus.RESOLVED && resolvedValue.isPresent();
    }
}

