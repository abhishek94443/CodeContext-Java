package com.codecontext.core.security.rule;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.index.SymbolLookup;
import com.codecontext.core.model.SourceRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable analysis context provided to security rules during compilation unit inspection.
 */
public record CompilationUnitContext(
    Path filePath,
    String sourceCode,
    CompilationUnit compilationUnit,
    Optional<SymbolLookup> symbolLookup
) {
    public CompilationUnitContext {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(sourceCode, "sourceCode cannot be null");
        Objects.requireNonNull(compilationUnit, "compilationUnit cannot be null");
        symbolLookup = symbolLookup == null ? Optional.empty() : symbolLookup;
    }

    /**
     * Extracts precise 1-based SourceRange from any AST node.
     */
    public SourceRange toSourceRange(Node node) {
        if (node != null && node.getRange().isPresent()) {
            var r = node.getRange().get();
            return new SourceRange(
                Math.max(1, r.begin.line),
                Math.max(1, r.begin.column),
                Math.max(r.begin.line, r.end.line),
                Math.max(1, r.end.column)
            );
        }
        return new SourceRange(1, 1, 1, 1);
    }

    /**
     * Helper to construct a finding with rule metadata prefilled and exact node coordinates.
     */
    public Finding createFinding(
        RuleMetadata rule,
        String message,
        Node node,
        String evidence,
        String remediation
    ) {
        Objects.requireNonNull(rule, "rule cannot be null");
        return new Finding(
            rule.id(),
            rule.name(),
            rule.category(),
            rule.defaultSeverity(),
            message,
            Optional.of(filePath),
            Optional.of(toSourceRange(node)),
            evidence == null ? "" : evidence,
            remediation == null ? "" : remediation,
            rule.cweId(),
            AnalysisConfidence.HIGH,
            Map.of()
        );
    }
}

