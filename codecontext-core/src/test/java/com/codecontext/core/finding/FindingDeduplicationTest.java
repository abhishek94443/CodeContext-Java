package com.codecontext.core.finding;

import com.codecontext.core.model.SourceRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FindingDeduplicationTest {

    @Test
    @DisplayName("Deduplicates identical finding occurrences discovered across multiple analysis passes")
    void testFindingDeduplicationAcrossPasses() {
        Finding finding1 = new Finding(
                "CC-CRYPTO-001",
                "Insecure ECB Mode Encryption",
                FindingCategory.CRYPTOGRAPHY,
                FindingSeverity.HIGH,
                "Insecure Electronic Codebook mode selected",
                Optional.of(Path.of("src/CryptoService.java")),
                Optional.of(new SourceRange(21, 9, 21, 55)),
                "Cipher.getInstance(\"AES/ECB/PKCS5Padding\")",
                "Use AES-GCM",
                Optional.of("CWE-327"),
                AnalysisConfidence.HIGH,
                java.util.Map.of()
        );

        // Identical occurrence created through a separate pass (e.g. Pass 2 invocation resolver hook)
        Finding finding2 = new Finding(
                "CC-CRYPTO-001",
                "Insecure ECB Mode Encryption",
                FindingCategory.CRYPTOGRAPHY,
                FindingSeverity.HIGH,
                "Insecure Electronic Codebook mode selected",
                Optional.of(Path.of("src/CryptoService.java")),
                Optional.of(new SourceRange(21, 9, 21, 55)),
                "Cipher.getInstance(\"AES/ECB/PKCS5Padding\")",
                "Use AES-GCM",
                Optional.of("CWE-327"),
                AnalysisConfidence.HIGH,
                java.util.Map.of()
        );

        List<Finding> deduped = Finding.deduplicate(List.of(finding1, finding2));

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0)).isEqualTo(finding1);
    }

    @Test
    @DisplayName("Preserves distinct finding occurrences across different lines and rules")
    void testPreservesDistinctFindings() {
        Finding finding1 = new Finding(
                "CC-CRYPTO-001",
                "Rule 1",
                FindingCategory.CRYPTOGRAPHY,
                FindingSeverity.HIGH,
                "Message 1",
                Optional.of(Path.of("src/A.java")),
                Optional.of(new SourceRange(10, 1, 10, 20)),
                "Evidence 1",
                "",
                Optional.empty(),
                AnalysisConfidence.HIGH,
                java.util.Map.of()
        );

        Finding finding2 = new Finding(
                "CC-CRYPTO-002",
                "Rule 2",
                FindingCategory.CRYPTOGRAPHY,
                FindingSeverity.HIGH,
                "Message 2",
                Optional.of(Path.of("src/A.java")),
                Optional.of(new SourceRange(25, 1, 25, 20)),
                "Evidence 2",
                "",
                Optional.empty(),
                AnalysisConfidence.HIGH,
                java.util.Map.of()
        );

        List<Finding> deduped = Finding.deduplicate(List.of(finding2, finding1, finding2));

        assertThat(deduped).hasSize(2);
        // Deterministic sorting puts line 10 before line 25
        assertThat(deduped.get(0).ruleId()).isEqualTo("CC-CRYPTO-001");
        assertThat(deduped.get(1).ruleId()).isEqualTo("CC-CRYPTO-002");
    }
}

