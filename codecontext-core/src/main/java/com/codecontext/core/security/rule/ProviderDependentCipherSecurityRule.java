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
 * CC-CRYPTO-004: Provider-Dependent Default Cipher Transformation
 * Detects cipher transformations that omit explicit mode and padding specifications (e.g. Cipher.getInstance("AES")).
 * Omitting mode and padding causes JCA to fall back to provider-dependent defaults (for example, SunJCE defaults "AES"
 * to ECB mode: "AES/ECB/PKCS5Padding"), leading to insecure encryption or unpredictable runtime behavior across providers.
 */
public class ProviderDependentCipherSecurityRule extends AstVisitorSecurityRule {

    public static final String RULE_ID = "CC-CRYPTO-004";

    private static final RuleMetadata METADATA = new RuleMetadata(
            RULE_ID,
            "Provider-Dependent Default Cipher Transformation",
            FindingCategory.CRYPTOGRAPHY,
            FindingSeverity.MEDIUM,
            "Detects cipher transformations omitting explicit mode and padding specifications.",
            "Specifying only the algorithm name (e.g. Cipher.getInstance(\"AES\")) relies on provider-dependent defaults. " +
                    "In the default SunJCE provider, this implicitly selects insecure ECB mode (AES/ECB/PKCS5Padding). " +
                    "Across different JCE providers, defaults vary unpredictably.",
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
        if (algorithm.isBareAlgorithmWithoutModeOrPadding()) {
            Finding finding = new Finding(
                    metadata().id(),
                    metadata().name(),
                    metadata().category(),
                    metadata().defaultSeverity(),
                    "Cipher transformation '" + algorithm.rawName() +
                            "' omits explicit mode and padding specifications. Relying on provider defaults may result in insecure mode selection (e.g. SunJCE defaults to ECB mode) or unpredictable behavior across JCE providers.",
                    Optional.of(context.filePath()),
                    Optional.of(context.toSourceRange(call)),
                    "API call: " + call + " with bare transformation '" + algorithm.rawName() + "'",
                    "Explicitly specify the full transformation string with an authenticated cipher mode and padding, such as 'AES/GCM/NoPadding'.",
                    metadata().cweId(),
                    AnalysisConfidence.HIGH,
                    Map.of(
                            "algorithm", algorithm.rawName(),
                            "cipherFamily", algorithm.family(),
                            "issue", "UNSPECIFIED_MODE_AND_PADDING"
                    )
            );
            findings.add(finding);
        }
    }
}

