package com.codecontext.cli.export;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.model.SourceRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SarifExporterTest {

    @Test
    @DisplayName("SarifExporter exports custom security findings with exact coordinates, CWE, and POSIX path")
    void testExportCustomSecurityFindings(@TempDir Path tempRepo) throws IOException {
        Path sourceFile = tempRepo.resolve("src/com/example/CryptoService.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "package com.example; class CryptoService {}");

        RuleMetadata rule = RuleMetadata.of(
                "SEC001",
                "WeakCipherAlgorithm",
                FindingCategory.CRYPTOGRAPHY,
                FindingSeverity.HIGH,
                "Weak cryptographic algorithm in use.",
                "The DES algorithm is considered broken. Replace with AES-GCM.",
                "https://cwe.mitre.org/data/definitions/327.html",
                "CWE-327"
        );

        Finding finding = Finding.builder()
                .rule(rule)
                .message("Use of broken DES cipher detected")
                .filePath(sourceFile)
                .location(new SourceRange(15, 8, 15, 32))
                .evidence("Cipher.getInstance(\"DES\")")
                .remediation("Use AES/GCM/NoPadding")
                .property("algorithm", "DES")
                .build();

        Path sarifOutput = tempRepo.resolve("results.sarif");
        SarifExporter exporter = new SarifExporter();
        exporter.exportFindings(List.of(finding), List.of(rule), tempRepo, sarifOutput);

        assertThat(Files.exists(sarifOutput)).isTrue();
        String jsonText = Files.readString(sarifOutput);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonText);

        assertThat(root.get("version").asText()).isEqualTo("2.1.0");
        assertThat(root.get("$schema").asText()).contains("sarif-schema-2.1.0.json");

        // Tool Driver
        JsonNode driver = root.get("runs").get(0).get("tool").get("driver");
        assertThat(driver.get("name").asText()).isEqualTo("CodeContext");

        // Rule Registration
        JsonNode ruleNode = driver.get("rules").get(0);
        assertThat(ruleNode.get("id").asText()).isEqualTo("SEC001");
        assertThat(ruleNode.get("name").asText()).isEqualTo("WeakCipherAlgorithm");
        assertThat(ruleNode.get("defaultConfiguration").get("level").asText()).isEqualTo("error");
        assertThat(ruleNode.get("properties").get("cwe").asText()).isEqualTo("CWE-327");
        assertThat(ruleNode.get("helpUri").asText()).isEqualTo("https://cwe.mitre.org/data/definitions/327.html");

        // Result Location & Coordinates
        JsonNode result = root.get("runs").get(0).get("results").get(0);
        assertThat(result.get("ruleId").asText()).isEqualTo("SEC001");
        assertThat(result.get("level").asText()).isEqualTo("error");
        assertThat(result.get("message").get("text").asText()).isEqualTo("Use of broken DES cipher detected");

        JsonNode physLoc = result.get("locations").get(0).get("physicalLocation");
        String uri = physLoc.get("artifactLocation").get("uri").asText();
        assertThat(uri).doesNotContain("\\");
        assertThat(uri).isEqualTo("src/com/example/CryptoService.java");

        JsonNode region = physLoc.get("region");
        assertThat(region.get("startLine").asInt()).isEqualTo(15);
        assertThat(region.get("startColumn").asInt()).isEqualTo(8);
        assertThat(region.get("endLine").asInt()).isEqualTo(15);
        assertThat(region.get("endColumn").asInt()).isEqualTo(32);
    }

    @Test
    @DisplayName("SarifExporter outputs findings in deterministic sorted order")
    void testDeterministicResultOrdering(@TempDir Path tempRepo) {
        Path fileA = tempRepo.resolve("A.java");
        Path fileB = tempRepo.resolve("B.java");

        Finding f1 = Finding.builder().ruleId("SEC002").ruleName("R2").category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.MEDIUM).message("Finding B").filePath(fileB)
                .location(new SourceRange(5, 1, 5, 10)).build();

        Finding f2 = Finding.builder().ruleId("SEC001").ruleName("R1").category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.HIGH).message("Finding A").filePath(fileA)
                .location(new SourceRange(10, 1, 10, 10)).build();

        SarifExporter exporter = new SarifExporter();
        String json = exporter.generateSarifJson(List.of(f1, f2), List.of(), tempRepo);

        // A.java must appear before B.java in results
        int indexA = json.indexOf("A.java");
        int indexB = json.indexOf("B.java");
        assertThat(indexA).isLessThan(indexB);
    }

    @Test
    @DisplayName("SarifExporter omits region node when finding location is absent instead of fabricating line 1")
    void testFileLevelFindingWithoutRegion(@TempDir Path tempRepo) throws Exception {
        Path sourceFile = tempRepo.resolve("FileLevel.java");
        Files.writeString(sourceFile, "class FileLevel {}");

        Finding finding = Finding.builder()
                .ruleId("SEC003")
                .ruleName("FileLevelRule")
                .category(FindingCategory.CRYPTOGRAPHY)
                .severity(FindingSeverity.MEDIUM)
                .message("File-level cryptographic advisory")
                .filePath(sourceFile)
                // location is explicitly omitted (Optional.empty())
                .build();

        SarifExporter exporter = new SarifExporter();
        String json = exporter.generateSarifJson(List.of(finding), List.of(), tempRepo);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        JsonNode result = root.get("runs").get(0).get("results").get(0);
        JsonNode physLoc = result.get("locations").get(0).get("physicalLocation");

        assertThat(physLoc.get("artifactLocation").get("uri").asText()).isEqualTo("FileLevel.java");
        // Verify region is NOT present (no fabricated line 1)
        assertThat(physLoc.has("region")).isFalse();
    }

    @Test
    @DisplayName("Validates full SARIF 2.1.0 JSON structure against OASIS specification constraints")
    void testSarif210SchemaStructureAndCoordinateValidation(@TempDir Path tempRepo) throws Exception {
        Path sourceFile = tempRepo.resolve("src/Crypto.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "class Crypto {}");

        RuleMetadata rule = RuleMetadata.of(
                "CC-CRYPTO-001",
                "Insecure ECB Mode Encryption",
                FindingCategory.CRYPTOGRAPHY,
                FindingSeverity.HIGH,
                "ECB mode encryption.",
                "Insecure electronic codebook mode."
        );

        Finding finding = Finding.builder()
                .rule(rule)
                .message("Insecure ECB mode selected")
                .filePath(sourceFile)
                .location(new SourceRange(42, 10, 42, 60))
                .evidence("Cipher.getInstance(\"AES/ECB/PKCS5Padding\")")
                .remediation("Use AES-GCM")
                .build();

        SarifExporter exporter = new SarifExporter();
        String json = exporter.generateSarifJson(List.of(finding), List.of(rule), tempRepo);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // SARIF root
        assertThat(root.hasNonNull("$schema")).isTrue();
        assertThat(root.get("version").asText()).isEqualTo("2.1.0");
        assertThat(root.get("runs").isArray()).isTrue();

        // Run
        JsonNode run = root.get("runs").get(0);
        JsonNode driver = run.get("tool").get("driver");
        assertThat(driver.get("name").asText()).isEqualTo("CodeContext");
        assertThat(driver.get("version").asText()).isNotEmpty();
        assertThat(driver.get("informationUri").asText()).isNotEmpty();

        // Rules array
        JsonNode rules = driver.get("rules");
        assertThat(rules.isArray()).isTrue();
        JsonNode r0 = rules.get(0);
        assertThat(r0.get("id").asText()).isEqualTo("CC-CRYPTO-001");
        assertThat(r0.get("shortDescription").get("text").asText()).isNotEmpty();
        assertThat(r0.get("fullDescription").get("text").asText()).isNotEmpty();
        assertThat(r0.get("defaultConfiguration").get("level").asText()).isIn("error", "warning", "note", "none");

        // Results array
        JsonNode results = run.get("results");
        assertThat(results.isArray()).isTrue();
        JsonNode res0 = results.get(0);
        assertThat(res0.get("ruleId").asText()).isEqualTo("CC-CRYPTO-001");
        assertThat(res0.get("level").asText()).isIn("error", "warning", "note", "none");
        assertThat(res0.get("message").get("text").asText()).isNotEmpty();

        // Physical Location & Region
        JsonNode region = res0.get("locations").get(0).get("physicalLocation").get("region");
        assertThat(region.get("startLine").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(region.get("startColumn").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(region.get("endLine").asInt()).isGreaterThanOrEqualTo(region.get("startLine").asInt());
        assertThat(region.get("endColumn").asInt()).isGreaterThanOrEqualTo(1);
    }
}

