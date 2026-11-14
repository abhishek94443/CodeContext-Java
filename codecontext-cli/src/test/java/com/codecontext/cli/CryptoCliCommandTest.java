package com.codecontext.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoCliCommandTest {

    private final StringWriter outCapture = new StringWriter();
    private final StringWriter errCapture = new StringWriter();
    private final PrintWriter out = new PrintWriter(outCapture);
    private final PrintWriter err = new PrintWriter(errCapture);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("codecontext crypto displays usage and lists inventory, cbom, and pqc subcommands")
    void testCryptoCommandUsage() {
        int exitCode = CodeContextLauncher.execute(new String[]{"crypto"}, out, err);
        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();
        assertThat(stdout).contains("inventory");
        assertThat(stdout).contains("cbom");
        assertThat(stdout).contains("pqc");
    }

    @Test
    @DisplayName("codecontext crypto inventory with invalid path returns BAD_ARGS exit code")
    void testCryptoInventoryNonexistentPath() {
        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "inventory", "nonexistent-dir-path-12345"
        }, out, err);
        assertThat(exitCode).isEqualTo(CliExitCode.BAD_ARGS.getCode());
        assertThat(errCapture.toString()).contains("Repository path does not exist");
    }

    @Test
    @DisplayName("codecontext crypto inventory --format text outputs tabular crypto inventory")
    void testCryptoInventoryTextFormat(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "inventory", tempRepo.toString(),
                "--format", "text"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();
        assertThat(stdout).contains("CodeContext Cryptographic Asset Inventory");
        assertThat(stdout).contains("Files Scanned");
        assertThat(stdout).contains("Total Observations");
        assertThat(stdout).contains("AES/GCM/NoPadding");
        assertThat(stdout).contains("SHA-256");
    }

    @Test
    @DisplayName("codecontext crypto inventory --format json prints valid structured JSON")
    void testCryptoInventoryJsonFormat(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "inventory", tempRepo.toString(),
                "--format", "json"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();

        JsonNode root = mapper.readTree(stdout);
        assertThat(root.has("summary")).isTrue();
        assertThat(root.has("assets")).isTrue();

        JsonNode summary = root.get("summary");
        assertThat(summary.get("totalObservations").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(summary.get("uniqueAssets").asInt()).isGreaterThanOrEqualTo(2);

        JsonNode assets = root.get("assets");
        assertThat(assets.isArray()).isTrue();
        boolean foundAes = false;
        boolean foundSha = false;
        for (JsonNode asset : assets) {
            String name = asset.get("name").asText();
            if (name.contains("AES")) foundAes = true;
            if (name.contains("SHA-256")) foundSha = true;
        }
        assertThat(foundAes).isTrue();
        assertThat(foundSha).isTrue();
    }

    @Test
    @DisplayName("codecontext crypto inventory -o writes json to target file")
    void testCryptoInventoryOutputFile(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);
        Path outputFile = tempRepo.resolve("my-inventory.json");

        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "inventory", tempRepo.toString(),
                "-o", outputFile.toString()
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(Files.exists(outputFile)).isTrue();

        JsonNode root = mapper.readTree(outputFile.toFile());
        assertThat(root.has("summary")).isTrue();
        assertThat(root.has("assets")).isTrue();
    }

    @Test
    @DisplayName("codecontext crypto cbom generates official CycloneDX 1.6 CBOM document")
    void testCryptoCbomGeneration(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);
        Path cbomFile = tempRepo.resolve("custom-cbom.json");

        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "cbom", tempRepo.toString(),
                "-o", cbomFile.toString()
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(Files.exists(cbomFile)).isTrue();

        JsonNode root = mapper.readTree(cbomFile.toFile());
        assertThat(root.get("bomFormat").asText()).isEqualTo("CycloneDX");
        assertThat(root.get("specVersion").asText()).isEqualTo("1.6");
        assertThat(root.has("components")).isTrue();

        JsonNode components = root.get("components");
        assertThat(components.isArray()).isTrue();
        assertThat(components.size()).isGreaterThanOrEqualTo(2);

        // Verify cryptographic assets contain cryptoProperties
        for (JsonNode comp : components) {
            assertThat(comp.get("type").asText()).isEqualTo("cryptographic-asset");
            assertThat(comp.has("cryptoProperties")).isTrue();
            JsonNode crypto = comp.get("cryptoProperties");
            assertThat(crypto.get("assetType").asText()).isEqualTo("algorithm");
            assertThat(crypto.has("algorithmProperties")).isTrue();
        }
    }

    @Test
    @DisplayName("codecontext security scan --format html generates standalone HTML security dashboard")
    void testSecurityScanHtmlDashboard(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);
        Path htmlFile = tempRepo.resolve("custom-dashboard.html");

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(),
                "--format", "html",
                "-o", htmlFile.toString()
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(Files.exists(htmlFile)).isTrue();

        String html = Files.readString(htmlFile);
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("CodeContext Security Intelligence");
        assertThat(html).contains("id=\"security-payload\"");
        assertThat(html).contains("id=\"tab-findings\"");
        assertThat(html).contains("id=\"tab-inventory\"");
        assertThat(html).contains("id=\"tab-visualizations\"");
        assertThat(html).contains("id=\"tab-pqc\"");
        assertThat(html).contains("PQC Migration");
        assertThat(html).contains("id=\"tab-limitations\"");
    }

    @Test
    @DisplayName("codecontext crypto pqc --format table evaluates quantum exposure and prints report")
    void testCryptoPqcTableFormat(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "pqc", tempRepo.toString(),
                "--format", "table"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();
        assertThat(stdout).contains("CodeContext Post-Quantum Cryptography (PQC) Migration Assessment");
        assertThat(stdout).contains("Total Cryptographic Assets");
        assertThat(stdout).contains("Quantum Impacted");
        assertThat(stdout).contains("AES/GCM/NoPadding");
        assertThat(stdout).contains("SYMMETRIC_ENCRYPTION");
    }

    @Test
    @DisplayName("codecontext crypto pqc --format json returns structured PQC assessment")
    void testCryptoPqcJsonFormat(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "pqc", tempRepo.toString(),
                "--format", "json"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();

        JsonNode root = mapper.readTree(stdout);
        assertThat(root.has("summary")).isTrue();
        assertThat(root.has("assessments")).isTrue();

        JsonNode summary = root.get("summary");
        assertThat(summary.get("totalAssets").asInt()).isEqualTo(2);
        assertThat(summary.get("quantumImpacted").asInt()).isEqualTo(2);

        JsonNode assessments = root.get("assessments");
        assertThat(assessments.isArray()).isTrue();
        assertThat(assessments.size()).isEqualTo(2);

        JsonNode aesAssessment = assessments.get(0);
        assertThat(aesAssessment.has("exposureLevel")).isTrue();
        assertThat(aesAssessment.has("category")).isTrue();
        assertThat(aesAssessment.has("recommendation")).isTrue();
        assertThat(aesAssessment.has("blastRadius")).isTrue();
        assertThat(summary.has("pqcMigrationCandidates")).isTrue();
        assertThat(summary.has("quantumConsiderations")).isTrue();
    }

    @Test
    @DisplayName("codecontext crypto pqc -o output.json writes valid PQC report to disk")
    void testCryptoPqcFileOutput(@TempDir Path tempRepo) throws IOException {
        createSampleCryptoRepository(tempRepo);
        Path outputFile = tempRepo.resolve("pqc-report.json");

        int exitCode = CodeContextLauncher.execute(new String[]{
                "crypto", "pqc", tempRepo.toString(),
                "-o", outputFile.toString()
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(Files.exists(outputFile)).isTrue();

        JsonNode root = mapper.readTree(Files.readString(outputFile));
        assertThat(root.get("summary").get("totalAssets").asInt()).isEqualTo(2);
    }

    private void createSampleCryptoRepository(Path root) throws IOException {
        Path src = root.resolve("src/com/example");
        Files.createDirectories(src);

        String serviceCode = """
                package com.example;
                import javax.crypto.Cipher;
                import java.security.MessageDigest;
                public class EncryptionService {
                    public void encrypt() throws Exception {
                        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                    }
                    public void hash() throws Exception {
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                    }
                }
                """;
        Files.writeString(src.resolve("EncryptionService.java"), serviceCode);
    }
}
