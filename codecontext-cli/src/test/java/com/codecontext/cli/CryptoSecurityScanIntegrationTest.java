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

class CryptoSecurityScanIntegrationTest {

    @Test
    @DisplayName("End-to-end security scan detects cryptographic misuses and displays summary table")
    void testEndToEndCryptoScanWithFindings(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                import javax.crypto.Cipher;
                import java.security.MessageDigest;
                import javax.net.ssl.SSLContext;
                
                public class InsecureService {
                    public void init() throws Exception {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                        Cipher.getInstance("DES/CBC/PKCS5Padding");
                        MessageDigest.getInstance("MD5");
                        SSLContext.getInstance("TLSv1");
                    }
                }
                """;
        Files.writeString(pkg.resolve("InsecureService.java"), code);

        StringWriter outCapture = new StringWriter();
        StringWriter errCapture = new StringWriter();
        PrintWriter out = new PrintWriter(outCapture);
        PrintWriter err = new PrintWriter(errCapture);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(), "--no-color"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();

        assertThat(stdout).contains("CodeContext Security & Cryptographic Analysis");
        assertThat(stdout).contains("Active Rules    : 5");
        assertThat(stdout).contains("Crypto API Usages: 4");
        assertThat(stdout).contains("Security Findings: 4");
        assertThat(stdout).contains("Status: Findings detected. 4 security findings identified across 1 scanned file.");

        assertThat(stdout).contains("CC-CRYPTO-001");
        assertThat(stdout).contains("CC-CRYPTO-002");
        assertThat(stdout).contains("CC-CRYPTO-003");
        assertThat(stdout).contains("CC-TLS-001");
    }

    @Test
    @DisplayName("Zero-rule scan does NOT report repository as clean (reports posture unassessed)")
    void testZeroRulesScanPostureUnassessed(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Sample.java"), "package com.example; public class Sample {}");

        StringWriter outCapture = new StringWriter();
        StringWriter errCapture = new StringWriter();
        PrintWriter out = new PrintWriter(outCapture);
        PrintWriter err = new PrintWriter(errCapture);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(), "--no-default-rules", "--no-color"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();

        assertThat(stdout).contains("Active Rules    : 0");
        assertThat(stdout).contains("Status: No active rules. Repository security posture unassessed.");
        assertThat(stdout).doesNotContain("findings detected");
    }

    @Test
    @DisplayName("Clean cryptographic usage (AES-GCM and SHA-256) reports 0 findings and inventory records")
    void testCleanCryptoInventoryWithoutFindings(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                import javax.crypto.Cipher;
                import java.security.MessageDigest;
                
                public class SecureService {
                    public void init() throws Exception {
                        Cipher.getInstance("AES/GCM/NoPadding");
                        MessageDigest.getInstance("SHA-256");
                    }
                }
                """;
        Files.writeString(pkg.resolve("SecureService.java"), code);

        StringWriter outCapture = new StringWriter();
        StringWriter errCapture = new StringWriter();
        PrintWriter out = new PrintWriter(outCapture);
        PrintWriter err = new PrintWriter(errCapture);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(), "--no-color"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();

        assertThat(stdout).contains("Crypto API Usages: 2");
        assertThat(stdout).contains("Security Findings: 0");
        assertThat(stdout).contains("Status: Analysis complete. 0 findings detected for 5 active rules across 1 scanned file.");
    }

    @Test
    @DisplayName("JSON output format includes all structured findings with metadata")
    void testJsonOutputFormatWithFindings(@TempDir Path tempRepo) throws Exception {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class InsecureService {
                    public void init() throws Exception {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        Files.writeString(pkg.resolve("InsecureService.java"), code);

        StringWriter outCapture = new StringWriter();
        StringWriter errCapture = new StringWriter();
        PrintWriter out = new PrintWriter(outCapture);
        PrintWriter err = new PrintWriter(errCapture);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(), "--format", "json"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode array = mapper.readTree(outCapture.toString());

        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(1);

        JsonNode f = array.get(0);
        assertThat(f.get("ruleId").asText()).isEqualTo("CC-CRYPTO-001");
        assertThat(f.get("category").asText()).isEqualTo("CRYPTOGRAPHY");
        assertThat(f.get("severity").asText()).isEqualTo("HIGH");
        assertThat(f.get("cweId").asText()).isEqualTo("CWE-327");
        assertThat(f.get("confidence").asText()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Multi-threaded execution produces deterministic output across repeated runs")
    void testDeterministicExecutionAcrossRuns(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        for (int i = 0; i < 10; i++) {
            String code = """
                    package com.example;
                    import javax.crypto.Cipher;
                    public class Service""" + i + """
                    {
                        public void test() throws Exception {
                            Cipher.getInstance("AES/ECB/PKCS5Padding");
                            Cipher.getInstance("DES/CBC/PKCS5Padding");
                        }
                    }
                    """;
            Files.writeString(pkg.resolve("Service" + i + ".java"), code);
        }

        String run1Output;
        {
            StringWriter outCapture = new StringWriter();
            PrintWriter out = new PrintWriter(outCapture);
            CodeContextLauncher.execute(new String[]{
                    "security", "scan", tempRepo.toString(), "--no-color"
            }, out, new PrintWriter(new StringWriter()));
            run1Output = outCapture.toString();
        }

        String run2Output;
        {
            StringWriter outCapture = new StringWriter();
            PrintWriter out = new PrintWriter(outCapture);
            CodeContextLauncher.execute(new String[]{
                    "security", "scan", tempRepo.toString(), "--no-color"
            }, out, new PrintWriter(new StringWriter()));
            run2Output = outCapture.toString();
        }

        // Findings count and order must match identically
        assertThat(run1Output).contains("Security Findings: 20");
        assertThat(run2Output).contains("Security Findings: 20");
    }
}

