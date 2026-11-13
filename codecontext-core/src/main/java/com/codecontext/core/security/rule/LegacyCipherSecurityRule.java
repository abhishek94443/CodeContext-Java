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
 * CC-CRYPTO-002: Legacy Insecure Cipher
 * Detects legacy, broken, or deprecated symmetric cipher algorithms (DES, RC2, RC4/ARCFOUR, Blowfish, 3DES/DESede).
 * Broken ciphers are reported with HIGH severity; 3DES is reported with MEDIUM severity per NIST SP 800-131A Rev 2 deprecation.
 */
public class LegacyCipherSecurityRule extends AstVisitorSecurityRule {

    public static final String RULE_ID = "CC-CRYPTO-002";

    private static final RuleMetadata METADATA = new RuleMetadata(
            RULE_ID,
            "Legacy Insecure Cipher",
            FindingCategory.CRYPTOGRAPHY,
            FindingSeverity.HIGH,
            "Detects broken or deprecated symmetric encryption algorithms such as DES, RC2, RC4, or 3DES.",
            "Legacy ciphers have insufficient key lengths (e.g. 56-bit DES), 64-bit block sizes vulnerable to collision attacks " +
                    "(Sweet32), or stream cipher biases (RC4). Modern standards mandate AES or ChaCha20.",
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

        if (algorithm.isLegacyInsecureCipher()) {
            Finding finding = new Finding(
                    metadata().id(),
                    metadata().name(),
                    metadata().category(),
                    FindingSeverity.HIGH,
                    "Broken legacy cipher algorithm selected: '" + algorithm.rawName() +
                            "'. Algorithm is vulnerable to practical cryptanalysis and does not meet modern security baselines.",
                    Optional.of(context.filePath()),
                    Optional.of(context.toSourceRange(call)),
                    "API call: " + call + " with resolved cipher '" + algorithm.rawName() + "'",
                    "Replace with modern AES (e.g. AES/GCM/NoPadding) or ChaCha20-Poly1305.",
                    metadata().cweId(),
                    AnalysisConfidence.HIGH,
                    Map.of(
                            "algorithm", algorithm.rawName(),
                            "cipherFamily", algorithm.family(),
                            "status", "BROKEN"
                    )
            );
            findings.add(finding);
        } else if (algorithm.isTripleDes()) {
            Finding finding = new Finding(
                    metadata().id(),
                    metadata().name(),
                    metadata().category(),
                    FindingSeverity.MEDIUM,
                    "Deprecated Triple DES (3DES/DESede) cipher selected: '" + algorithm.rawName() +
                            "'. 3DES has been retired by NIST (SP 800-131A Rev 2) after 2023 due to 64-bit block collision vulnerabilities.",
                    Optional.of(context.filePath()),
                    Optional.of(context.toSourceRange(call)),
                    "API call: " + call + " with resolved cipher '" + algorithm.rawName() + "'",
                    "Migrate from 3DES/DESede to modern AES-GCM (e.g. AES/GCM/NoPadding).",
                    metadata().cweId(),
                    AnalysisConfidence.HIGH,
                    Map.of(
                            "algorithm", algorithm.rawName(),
                            "cipherFamily", algorithm.family(),
                            "status", "DEPRECATED"
                    )
            );
            findings.add(finding);
        }
    }
}
