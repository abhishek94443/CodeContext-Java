package com.codecontext.core.finding;

import com.codecontext.core.model.SourceRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FindingTest {

    @Test
    @DisplayName("Finding enforces non-null invariants for mandatory fields")
    void testFindingInvariants() {
        assertThatThrownBy(() -> new Finding(null, "Rule", FindingCategory.CRYPTOGRAPHY, FindingSeverity.HIGH, "msg",
                Optional.empty(), Optional.empty(), "", "", Optional.empty(), AnalysisConfidence.HIGH, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ruleId");

        assertThatThrownBy(() -> new Finding("ID", null, FindingCategory.CRYPTOGRAPHY, FindingSeverity.HIGH, "msg",
                Optional.empty(), Optional.empty(), "", "", Optional.empty(), AnalysisConfidence.HIGH, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ruleName");

        assertThatThrownBy(() -> new Finding("ID", "Rule", null, FindingSeverity.HIGH, "msg",
                Optional.empty(), Optional.empty(), "", "", Optional.empty(), AnalysisConfidence.HIGH, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("category");

        assertThatThrownBy(() -> new Finding("ID", "Rule", FindingCategory.CRYPTOGRAPHY, null, "msg",
                Optional.empty(), Optional.empty(), "", "", Optional.empty(), AnalysisConfidence.HIGH, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("severity");

        assertThatThrownBy(() -> new Finding("ID", "Rule", FindingCategory.CRYPTOGRAPHY, FindingSeverity.HIGH, null,
                Optional.empty(), Optional.empty(), "", "", Optional.empty(), AnalysisConfidence.HIGH, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message");
    }

    @Test
    @DisplayName("Finding builder sets all fields and creates immutable instance")
    void testFindingBuilder() {
        RuleMetadata meta = RuleMetadata.of(
                "SEC001",
                "WeakCipher",
                FindingCategory.CRYPTOGRAPHY,
                FindingSeverity.HIGH,
                "Weak cipher used",
                "Use AES-GCM instead",
                "https://example.com/sec001",
                "CWE-327"
        );

        Finding finding = Finding.builder()
                .rule(meta)
                .message("Insecure DES cipher detected")
                .filePath(Path.of("src/Crypto.java"))
                .location(new SourceRange(10, 5, 10, 25))
                .evidence("Cipher.getInstance(\"DES\")")
                .remediation("Migrate to AES/GCM/NoPadding")
                .property("cipher", "DES")
                .build();

        assertThat(finding.ruleId()).isEqualTo("SEC001");
        assertThat(finding.ruleName()).isEqualTo("WeakCipher");
        assertThat(finding.category()).isEqualTo(FindingCategory.CRYPTOGRAPHY);
        assertThat(finding.severity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(finding.message()).isEqualTo("Insecure DES cipher detected");
        assertThat(finding.filePath()).contains(Path.of("src/Crypto.java"));
        assertThat(finding.location()).contains(new SourceRange(10, 5, 10, 25));
        assertThat(finding.evidence()).isEqualTo("Cipher.getInstance(\"DES\")");
        assertThat(finding.remediation()).isEqualTo("Migrate to AES/GCM/NoPadding");
        assertThat(finding.cweId()).contains("CWE-327");
        assertThat(finding.confidence()).isEqualTo(AnalysisConfidence.HIGH);
        assertThat(finding.properties()).containsEntry("cipher", "DES");
    }

    @Test
    @DisplayName("Finding provides deterministic sorting by path, line, column, ruleId, severity, message")
    void testDeterministicSorting() {
        Finding f1 = Finding.builder().ruleId("SEC002").ruleName("R2").category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.MEDIUM).message("Msg B")
                .filePath(Path.of("B.java")).location(new SourceRange(5, 1, 5, 10)).build();

        Finding f2 = Finding.builder().ruleId("SEC001").ruleName("R1").category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.HIGH).message("Msg A")
                .filePath(Path.of("A.java")).location(new SourceRange(10, 1, 10, 10)).build();

        Finding f3 = Finding.builder().ruleId("SEC001").ruleName("R1").category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.HIGH).message("Msg C")
                .filePath(Path.of("A.java")).location(new SourceRange(5, 1, 5, 10)).build();

        Finding f4 = Finding.builder().ruleId("SEC001").ruleName("R1").category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.HIGH).message("Msg D")
                .filePath(Path.of("A.java")).location(new SourceRange(5, 5, 5, 15)).build();

        List<Finding> list = new ArrayList<>(List.of(f1, f2, f3, f4));
        Collections.sort(list);

        assertThat(list).containsExactly(f3, f4, f2, f1);
    }
}

