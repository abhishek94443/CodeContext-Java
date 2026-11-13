package com.codecontext.cli.export.security;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.export.HtmlReportExporter;
import com.codecontext.core.graph.export.HtmlReportPayload;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.TypeKind;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoInventorySummary;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureReportIndependenceTest {

    @Test
    @DisplayName("Architecture report and Security report remain completely decoupled and independent")
    void testBothReportsCoexistIndependently(@TempDir Path tempDir) throws IOException {
        // 1. Generate Architecture Report using core HtmlReportExporter
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);
        TypeVertex vertex = TypeVertex.of("com.example.OrderService", "OrderService", "com.example", TypeKind.CLASS, Path.of("OrderService.java"));
        graph.addVertex(vertex);

        HtmlReportPayload archPayload = new HtmlReportPayload(
                graph,
                Collections.emptyList(),
                new CycleReport(Collections.emptyList(), 0, false),
                Collections.emptyList()
        );
        HtmlReportExporter archExporter = new HtmlReportExporter();
        Path archOutputFile = tempDir.resolve("codecontext-graph.html");
        archExporter.export(archPayload, archOutputFile);

        // 2. Generate Security Report using SecurityHtmlReportExporter
        Finding secFinding = Finding.builder()
                .ruleId("CC-CRYPTO-001")
                .ruleName("Weak Hash")
                .category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.HIGH)
                .message("MD5 is insecure")
                .evidence("MessageDigest.getInstance(\"MD5\")")
                .remediation("Upgrade to SHA-256")
                .confidence(AnalysisConfidence.HIGH)
                .build();

        SecurityAnalysisResult secResult = new SecurityAnalysisResult(
                List.of(secFinding),
                1, Collections.emptyList(), Collections.emptyList(), 10L, Collections.emptyList(), Collections.emptyList()
        );
        CryptoInventory secInventory = new CryptoInventory(
                Collections.emptyList(), Collections.emptyList(),
                new CryptoInventorySummary(0, 0, 0, 0, 0, Collections.emptyMap(), Collections.emptyMap())
        );
        SecurityReportPayload secPayload = new SecurityReportPayload(
                "test-repo", tempDir, Instant.now(), "2.0.0", secResult, secInventory, Collections.emptyList()
        );
        SecurityHtmlReportExporter secExporter = new SecurityHtmlReportExporter();
        Path secOutputFile = tempDir.resolve("codecontext-security.html");
        secExporter.export(secPayload, secOutputFile);

        // 3. Verify both files exist
        assertThat(Files.exists(archOutputFile)).isTrue();
        assertThat(Files.exists(secOutputFile)).isTrue();

        String archHtml = Files.readString(archOutputFile);
        String secHtml = Files.readString(secOutputFile);

        // Verify Architecture Report invariants
        assertThat(archHtml).contains("OrderService");
        assertThat(archHtml).contains("data-view=\"map\"");
        assertThat(archHtml).contains("id=\"graph\"");
        // Must NOT contain security dashboard specific markers
        assertThat(archHtml).doesNotContain("security-payload");
        assertThat(archHtml).doesNotContain("tab-findings");
        assertThat(archHtml).doesNotContain("tab-inventory");
        assertThat(archHtml).doesNotContain("CC-CRYPTO-001");

        // Verify Security Report invariants
        assertThat(secHtml).contains("CodeContext Security Intelligence");
        assertThat(secHtml).contains("id=\"security-payload\"");
        assertThat(secHtml).contains("id=\"tab-findings\"");
        assertThat(secHtml).contains("id=\"tab-inventory\"");
        assertThat(secHtml).contains("CC-CRYPTO-001");
        // Must NOT contain graph visualization specific markers
        assertThat(secHtml).doesNotContain("data-view=\"map\"");
        assertThat(secHtml).doesNotContain("ego-graph");
        assertThat(secHtml).doesNotContain("class=\"stage\"");
    }
}
