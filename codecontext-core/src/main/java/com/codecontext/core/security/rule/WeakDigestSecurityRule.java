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
 * CC-CRYPTO-003: Weak Message Digest Usage
 * Discovers MD2, MD4, MD5, and SHA-1 digest selections.
 * Reported as MEDIUM severity with context-sensitive review guidance, as weak digests may be used
 * for non-cryptographic checksums or backward-compatible interoperability.
 */
public class WeakDigestSecurityRule extends AstVisitorSecurityRule {

    public static final String RULE_ID = "CC-CRYPTO-003";

    private static final RuleMetadata METADATA = new RuleMetadata(
            RULE_ID,
            "Weak Message Digest Usage",
            FindingCategory.CRYPTOGRAPHY,
            FindingSeverity.MEDIUM,
            "Detects legacy or collision-broken hash algorithms such as MD5, MD2, or SHA-1.",
            "MD5 and SHA-1 suffer from practical collision attacks and are broken for digital signatures, " +
                    "certificates, and cryptographic integrity verification.",
            Optional.of("https://cwe.mitre.org/data/definitions/328.html"),
            Optional.of("CWE-328")
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
        if (usage.owner() != CryptoApiOwner.MESSAGE_DIGEST || !usage.isOwnerConfirmed()) {
            return;
        }

        if (usage.algorithm().isEmpty()) {
            return;
        }

        CryptoAlgorithm algorithm = usage.algorithm().get();
        if (algorithm.isWeakDigest()) {
            Finding finding = new Finding(
                    metadata().id(),
                    metadata().name(),
                    metadata().category(),
                    metadata().defaultSeverity(),
                    "Weak or collision-broken message digest algorithm '" + algorithm.rawName() +
                            "' selected. Verify this is not used for digital signatures, password hashing, or cryptographic integrity.",
                    Optional.of(context.filePath()),
                    Optional.of(context.toSourceRange(call)),
                    "API call: " + call + " with resolved digest '" + algorithm.rawName() + "'",
                    "Upgrade to SHA-256, SHA-384, SHA-512 (SHA-2 family) or SHA3-256 for cryptographic integrity. " +
                            "If used solely as an ordinary non-security checksum, document the non-cryptographic context.",
                    metadata().cweId(),
                    AnalysisConfidence.HIGH,
                    Map.of(
                            "algorithm", algorithm.rawName(),
                            "digestFamily", algorithm.family()
                    )
            );
            findings.add(finding);
        }
    }
}
