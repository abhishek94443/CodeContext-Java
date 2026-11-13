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
 * CC-CRYPTO-001: Insecure ECB Mode Encryption
 * Detects encryption transformations using Electronic Codebook (ECB) mode (e.g. AES/ECB/PKCS5Padding).
 * ECB mode encrypts identical plaintext blocks into identical ciphertext blocks, leaking structural data.
 */
public class EcbModeSecurityRule extends AstVisitorSecurityRule {

    public static final String RULE_ID = "CC-CRYPTO-001";

    private static final RuleMetadata METADATA = new RuleMetadata(
            RULE_ID,
            "Insecure ECB Mode Encryption",
            FindingCategory.CRYPTOGRAPHY,
            FindingSeverity.HIGH,
            "Detects cipher transformations configured with insecure Electronic Codebook (ECB) mode.",
            "Electronic Codebook (ECB) mode encrypts identical plaintext blocks into identical ciphertext blocks, " +
                    "failing to provide semantic security and exposing patterns in the underlying plaintext.",
            Optional.of("https://cwe.mitre.org/data/definitions/327.html"),
            Optional.of("CWE-327")
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
        if (usage.owner() != CryptoApiOwner.CIPHER || !usage.isOwnerConfirmed()) {
            return;
        }

        if (usage.algorithm().isEmpty()) {
            return;
        }

        CryptoAlgorithm algorithm = usage.algorithm().get();
        if (algorithm.isEcbMode()) {
            Finding finding = new Finding(
                    metadata().id(),
                    metadata().name(),
                    metadata().category(),
                    metadata().defaultSeverity(),
                    "Insecure Electronic Codebook (ECB) cipher mode selected: '" + algorithm.rawName() +
                            "'. ECB mode leaks plaintext block patterns and does not provide semantic security.",
                    Optional.of(context.filePath()),
                    Optional.of(context.toSourceRange(call)),
                    "API call: " + call + " with resolved transformation '" + algorithm.rawName() + "'",
                    "Replace ECB mode with an authenticated cipher mode such as AES/GCM/NoPadding or ChaCha20-Poly1305.",
                    metadata().cweId(),
                    AnalysisConfidence.HIGH,
                    Map.of(
                            "algorithm", algorithm.rawName(),
                            "cipherFamily", algorithm.family(),
                            "mode", "ECB"
                    )
            );
            findings.add(finding);
        }
    }
}
