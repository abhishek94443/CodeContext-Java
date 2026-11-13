package com.codecontext.cli.export.security;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.crypto.CryptoApiOwner;
import com.codecontext.core.security.crypto.ExpressionResolution;
import com.codecontext.core.security.inventory.CryptoAsset;
import com.codecontext.core.security.inventory.CryptoAssetRelationship;
import com.codecontext.core.security.inventory.CryptoAssetUsage;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoInventorySummary;
import com.codecontext.core.security.inventory.CryptoPrimitiveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHtmlReportExporterTest {

    private SecurityHtmlReportExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new SecurityHtmlReportExporter();
    }

    @Test
    @DisplayName("Exports complete standalone HTML report with all 6 required sections")
    void testExportsCompleteReportWithAllSections(@TempDir Path tempDir) throws IOException {
        Path fakeRepo = Path.of("/test/repo");
        Finding f1 = Finding.builder()
                .ruleId("CC-CRYPTO-001")
                .ruleName("Weak Cryptographic Algorithm: MD5")
                .category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.HIGH)
                .message("The MD5 message digest is cryptographically broken.")
                .filePath(Path.of("src/main/Auth.java"))
                .location(new SourceRange(14, 5, 14, 40))
                .evidence("MessageDigest.getInstance(\"MD5\")")
                .remediation("Migrate to SHA-256 or SHA-3.")
                .cweId("CWE-327")
                .confidence(AnalysisConfidence.HIGH)
                .build();

        SecurityAnalysisResult analysisResult = new SecurityAnalysisResult(
                List.of(f1),
                10,
                Collections.emptyList(),
                Collections.emptyList(),
                100L,
                Collections.emptyList(),
                Collections.emptyList()
        );

        CryptoAssetUsage u1 = new CryptoAssetUsage(
                "usage-1",
                Path.of("src/main/Auth.java"),
                new SourceRange(14, 5, 14, 40),
                Optional.of("com.example.Auth"),
                Optional.of("hashPassword"),
                CryptoApiOwner.MESSAGE_DIGEST,
                "getInstance",
                "\"MD5\"",
                ExpressionResolution.resolved("MD5", "\"MD5\"", "Resolved from literal"),
                AnalysisConfidence.HIGH
        );

        CryptoAsset a1 = new CryptoAsset(
                "asset-md5",
                "MD5",
                "MD5",
                CryptoPrimitiveType.MESSAGE_DIGEST,
                Optional.empty(),
                Optional.empty(),
                List.of(u1),
                true,
                AnalysisConfidence.HIGH
        );

        CryptoInventorySummary summary = new CryptoInventorySummary(
                1, 1, 1, 0, 1, Map.of("MD5", 1), Map.of(CryptoPrimitiveType.MESSAGE_DIGEST, 1)
        );

        CryptoInventory inventory = new CryptoInventory(
                List.of(a1),
                Collections.emptyList(),
                summary
        );

        SecurityReportPayload payload = new SecurityReportPayload(
                "test-repo",
                fakeRepo,
                Instant.now(),
                "2.0.0",
                analysisResult,
                inventory,
                List.of(RuleMetadata.of("CC-CRYPTO-001", "Weak Cryptographic Algorithm: MD5", FindingCategory.CRYPTOGRAPHY, FindingSeverity.HIGH, "MD5 is weak", "Full desc"))
        );

        Path outputFile = tempDir.resolve("codecontext-security.html");
        exporter.export(payload, outputFile);

        assertThat(Files.exists(outputFile)).isTrue();
        String html = Files.readString(outputFile);

        // Required Sections
        // 1. Overview cards & metadata
        assertThat(html).contains("CodeContext Security Intelligence");
        assertThat(html).contains("Files Scanned");
        assertThat(html).contains("Security Findings");
        assertThat(html).contains("Crypto Usages");
        assertThat(html).contains("Unique Assets");
        assertThat(html).contains("Unresolved Usages");

        // 2. Findings section & tabs
        assertThat(html).contains("id=\"tab-findings\"");
        assertThat(html).contains("CC-CRYPTO-001");
        assertThat(html).contains("The MD5 message digest is cryptographically broken");

        // 3. Cryptographic inventory section
        assertThat(html).contains("id=\"tab-inventory\"");
        assertThat(html).contains("asset-md5");
        assertThat(html).contains("MESSAGE_DIGEST");

        // 4. Visualizations section & charts
        assertThat(html).contains("id=\"tab-visualizations\"");
        assertThat(html).contains("id=\"chart-severity\"");
        assertThat(html).contains("id=\"chart-families\"");
        assertThat(html).contains("id=\"chart-top-files\"");
        assertThat(html).contains("id=\"chart-resolution-ratio\"");

        // 5. Unresolved observations section
        assertThat(html).contains("id=\"tab-unresolved\"");

        // 6. Analysis limitations section
        assertThat(html).contains("id=\"tab-limitations\"");
        assertThat(html).contains("Static Cryptographic Analysis Scope & Boundaries");
        assertThat(html).contains("Runtime TLS Interception");
        assertThat(html).contains("Local AST Scope");
    }

    @Test
    @DisplayName("HTML report is completely offline-capable with zero remote asset links")
    void testOfflineCapability(@TempDir Path tempDir) throws IOException {
        SecurityReportPayload payload = createEmptyPayload();
        Path outputFile = tempDir.resolve("offline-report.html");
        exporter.export(payload, outputFile);

        String html = Files.readString(outputFile);

        // Must not contain any external HTTP or HTTPS URLs, CDN scripts, or fonts
        assertThat(html).doesNotContain("http://");
        assertThat(html).doesNotContain("https://");
        assertThat(html).doesNotContain("cdnjs.cloudflare.com");
        assertThat(html).doesNotContain("fonts.googleapis.com");
        assertThat(html).doesNotContain("cdn.jsdelivr.net");
        assertThat(html).doesNotContain("<link rel=\"stylesheet\" href=\"http");
        assertThat(html).doesNotContain("<script src=\"http");
    }

    @Test
    @DisplayName("HTML export strictly neutralizes XSS and script injection in all dynamic fields")
    void testXssProtection(@TempDir Path tempDir) throws IOException {
        String xssPayload = "<script>alert('XSS-INJECTION')</script><img src=x onerror=alert(1)>";

        Finding maliciousFinding = Finding.builder()
                .ruleId("CC-XSS-001")
                .ruleName("Malicious Rule " + xssPayload)
                .category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.CRITICAL)
                .message("Malicious message: " + xssPayload)
                .filePath(Path.of("src/xss_test/Malicious.java"))
                .location(new SourceRange(1, 1, 1, 10))
                .evidence("Cipher.getInstance(\"" + xssPayload + "\")")
                .remediation("Remediation: " + xssPayload)
                .cweId("CWE-79")
                .confidence(AnalysisConfidence.HIGH)
                .build();

        CryptoAssetUsage maliciousUsage = new CryptoAssetUsage(
                "usage-xss-test",
                Path.of("src/xss_test/Malicious.java"),
                new SourceRange(1, 1, 1, 10),
                Optional.of("com.example.Malicious"),
                Optional.of("doCrypto"),
                CryptoApiOwner.CIPHER,
                "getInstance",
                xssPayload,
                ExpressionResolution.unknown(xssPayload, "Unresolved reason: " + xssPayload),
                AnalysisConfidence.LOW
        );

        CryptoAsset maliciousAsset = new CryptoAsset(
                "asset-" + xssPayload,
                "Algorithm-" + xssPayload,
                "Family-" + xssPayload,
                CryptoPrimitiveType.BLOCK_CIPHER,
                Optional.of("CBC-" + xssPayload),
                Optional.of("PKCS5Padding-" + xssPayload),
                List.of(maliciousUsage),
                false,
                AnalysisConfidence.LOW
        );

        CryptoInventory inventory = new CryptoInventory(
                List.of(maliciousAsset),
                Collections.emptyList(),
                new CryptoInventorySummary(1, 1, 0, 1, 1, Map.of("Family-" + xssPayload, 1), Map.of(CryptoPrimitiveType.BLOCK_CIPHER, 1))
        );

        SecurityAnalysisResult analysisResult = new SecurityAnalysisResult(
                List.of(maliciousFinding),
                1,
                Collections.emptyList(),
                Collections.emptyList(),
                50L,
                Collections.emptyList(),
                Collections.emptyList()
        );

        SecurityReportPayload payload = new SecurityReportPayload(
                "Repo-" + xssPayload,
                Path.of("/safe/path"),
                Instant.now(),
                "1.0-" + xssPayload,
                analysisResult,
                inventory,
                Collections.emptyList()
        );

        Path outputFile = tempDir.resolve("xss-test.html");
        exporter.export(payload, outputFile);

        String html = Files.readString(outputFile);

        // 1. The literal unescaped tag <script>alert('XSS-INJECTION')</script> must NOT appear in HTML
        // Check that any occurrence in the JSON payload is unicode-escaped: \u003cscript\u003e
        assertThat(html).doesNotContain("<script>alert('XSS-INJECTION')</script>");
        assertThat(html).doesNotContain("<img src=x onerror=alert(1)>");

        // 2. The repo name in the header must be entity escaped
        assertThat(html).contains("Repo-&lt;script&gt;alert(&#39;XSS-INJECTION&#39;)&lt;/script&gt;");

        // 3. The embedded JSON payload must encode '<' and '>' as \u003c and \u003e
        assertThat(html).contains("\\u003cscript\\u003ealert('XSS-INJECTION')\\u003c/script\\u003e");
    }

    @Test
    @DisplayName("Handles clean repository with zero findings and zero crypto assets cleanly")
    void testEmptyRepositoryPayload(@TempDir Path tempDir) throws IOException {
        SecurityReportPayload payload = createEmptyPayload();
        Path outputFile = tempDir.resolve("clean-report.html");
        exporter.export(payload, outputFile);

        String html = Files.readString(outputFile);
        assertThat(html).contains("Security Findings (0)");
        assertThat(html).contains("Cryptographic Inventory (0)");
        assertThat(html).contains("Unresolved Observations (0)");
        assertThat(html).contains("No security findings match the selected criteria.");
        assertThat(html).contains("No cryptographic assets match the selected criteria.");
    }

    private SecurityReportPayload createEmptyPayload() {
        SecurityAnalysisResult result = new SecurityAnalysisResult(
                Collections.emptyList(),
                5,
                Collections.emptyList(),
                Collections.emptyList(),
                10L,
                Collections.emptyList(),
                Collections.emptyList()
        );
        CryptoInventory inventory = new CryptoInventory(
                Collections.emptyList(),
                Collections.emptyList(),
                new CryptoInventorySummary(0, 0, 0, 0, 0, Collections.emptyMap(), Collections.emptyMap())
        );
        return new SecurityReportPayload(
                "clean-repo",
                Path.of("/clean/repo"),
                Instant.now(),
                "2.0.0",
                result,
                inventory,
                Collections.emptyList()
        );
    }
}
