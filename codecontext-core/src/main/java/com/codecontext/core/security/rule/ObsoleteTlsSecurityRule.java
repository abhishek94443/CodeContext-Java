package com.codecontext.core.security.rule;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.security.crypto.CryptoAlgorithm;
import com.codecontext.core.security.crypto.CryptoApiDetector;
import com.codecontext.core.security.crypto.CryptoApiOwner;
import com.codecontext.core.security.crypto.CryptoApiUsage;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CC-TLS-001: Obsolete TLS Protocol Selection
 * Detects explicit requests for deprecated and insecure TLS/SSL protocols (SSLv2, SSLv3, TLSv1, TLSv1.1)
 * via SSLContext.getInstance(...).
 * Does not flag modern protocols (TLSv1.2, TLSv1.3) or generic "TLS" configuration.
 */
public class ObsoleteTlsSecurityRule extends AstVisitorSecurityRule {

    public static final String RULE_ID = "CC-TLS-001";

    private static final RuleMetadata METADATA = new RuleMetadata(
            RULE_ID,
            "Obsolete TLS Protocol Selection",
            FindingCategory.TLS,
            FindingSeverity.HIGH,
            "Detects explicit requests for obsolete TLS/SSL protocols (SSLv2, SSLv3, TLS 1.0, TLS 1.1).",
            "RFC 8996 formally deprecated TLS 1.0 and TLS 1.1 due to inherent weaknesses and lack of support " +
                    "for modern authenticated ciphers. Insecure protocol versions expose communications to interception.",
            Optional.of("https://cwe.mitre.org/data/definitions/326.html"),
            Optional.of("CWE-326")
    );

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    protected void visitMethodCall(MethodCallExpr call, CompilationUnitContext context, List<Finding> findings) {
        Optional<CryptoApiUsage> usageOpt = CryptoApiDetector.detectMethodCall(call, context);
        if (usageOpt.isEmpty()) {
            return;
        }

        CryptoApiUsage usage = usageOpt.get();
        if (usage.owner() != CryptoApiOwner.SSL_CONTEXT || !usage.isOwnerConfirmed()) {
            return;
        }

        if (usage.algorithm().isEmpty()) {
            return;
        }

        CryptoAlgorithm algorithm = usage.algorithm().get();
        if (algorithm.isObsoleteTlsProtocol()) {
            Finding finding = new Finding(
                    metadata().id(),
                    metadata().name(),
                    metadata().category(),
                    metadata().defaultSeverity(),
                    "Obsolete and deprecated TLS protocol version requested: '" + algorithm.rawName() +
                            "'. RFC 8996 formally deprecated TLS 1.0 and TLS 1.1 due to protocol weaknesses.",
                    Optional.of(context.filePath()),
                    Optional.of(context.toSourceRange(call)),
                    "API call: " + call + " with requested protocol '" + algorithm.rawName() + "'",
                    "Request TLSv1.3 or TLSv1.2 (e.g. SSLContext.getInstance(\"TLSv1.3\")). Avoid explicit requests for TLSv1 or TLSv1.1.",
                    metadata().cweId(),
                    AnalysisConfidence.HIGH,
                    Map.of(
                            "protocol", algorithm.rawName(),
                            "status", "DEPRECATED"
                    )
            );
            findings.add(finding);
        }
    }
}
