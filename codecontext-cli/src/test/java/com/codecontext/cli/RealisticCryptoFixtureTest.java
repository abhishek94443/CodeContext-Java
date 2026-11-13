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

class RealisticCryptoFixtureTest {

    @Test
    @DisplayName("Comprehensive verification against realistic Java fixture with valid, invalid, dynamic, and non-JCA APIs")
    void testRealisticCryptoFixture(@TempDir Path projectDir) throws IOException {
        Path src = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(src);

        // 1. File with known cryptographic misuses
        String vulnerableCode = """
                package com.example;
                import javax.crypto.Cipher;
                import java.security.MessageDigest;
                import javax.net.ssl.SSLContext;
                
                public class VulnerableCryptoService {
                    public void execute() throws Exception {
                        // CC-CRYPTO-001: Explicit ECB mode
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                        // CC-CRYPTO-004: Bare transformation relying on provider default
                        Cipher.getInstance("AES");
                        // CC-CRYPTO-002: Broken 56-bit DES
                        Cipher.getInstance("DES/CBC/PKCS5Padding");
                        // CC-CRYPTO-002: Deprecated 3DES
                        Cipher.getInstance("DESede/CBC/PKCS5Padding");
                        // CC-CRYPTO-003: Broken MD5 digest
                        MessageDigest.getInstance("MD5");
                        // CC-TLS-001: Obsolete TLSv1
                        SSLContext.getInstance("TLSv1");
                    }
                }
                """;
        Files.writeString(src.resolve("VulnerableCryptoService.java"), vulnerableCode);

        // 2. File with safe modern cryptographic practices
        String secureCode = """
                package com.example;
                import javax.crypto.Cipher;
                import java.security.MessageDigest;
                import java.security.SecureRandom;
                import javax.net.ssl.SSLContext;
                
                public class SecureModernCryptoService {
                    public void execute() throws Exception {
                        Cipher.getInstance("AES/GCM/NoPadding");
                        MessageDigest.getInstance("SHA-256");
                        SSLContext.getInstance("TLSv1.3");
                        SecureRandom random = new SecureRandom();
                    }
                }
                """;
        Files.writeString(src.resolve("SecureModernCryptoService.java"), secureCode);

        // 3. File with dynamic/unresolved arguments (should be in inventory observations, 0 findings)
        String dynamicCode = """
                package com.example;
                import javax.crypto.Cipher;
                
                public class DynamicCryptoService {
                    public void execute(Config config, boolean flag) throws Exception {
                        // Dynamic method call -> UNKNOWN
                        String dynamicMode = config.getMode();
                        Cipher.getInstance(dynamicMode);
                        
                        // Conditional reassignment -> AMBIGUOUS
                        String branchMode = "AES/GCM/NoPadding";
                        if (flag) {
                            branchMode = "AES/ECB/PKCS5Padding";
                        }
                        Cipher.getInstance(branchMode);
                    }
                }
                class Config { String getMode() { return "AES/GCM"; } }
                """;
        Files.writeString(src.resolve("DynamicCryptoService.java"), dynamicCode);

        // 4. File with non-JCA classes and inner class collision (must not produce findings)
        String nonJcaCode = """
                package com.example;
                
                public class NonJcaService {
                    static class FakeCipher {
                        static Object getInstance(String mode) { return null; }
                    }
                    static class Cipher {
                        static Object getInstance(String mode) { return null; }
                    }
                    public void execute() {
                        FakeCipher.getInstance("AES/ECB/PKCS5Padding");
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        Files.writeString(src.resolve("NonJcaService.java"), nonJcaCode);

        // 5. File with conflicting wildcard imports (import javax.crypto.* AND import com.custom.*)
        String wildcardConflictCode = """
                package com.example;
                import javax.crypto.*;
                import com.custom.crypto.*;
                
                public class WildcardConflictService {
                    public void execute() throws Exception {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        Files.writeString(src.resolve("WildcardConflictService.java"), wildcardConflictCode);

        // Execute scan
        StringWriter outCapture = new StringWriter();
        StringWriter errCapture = new StringWriter();
        PrintWriter out = new PrintWriter(outCapture);
        PrintWriter err = new PrintWriter(errCapture);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", projectDir.toString(), "--no-color"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();

        // Verify summary
        assertThat(stdout).contains("Active Rules    : 5");
        // Total findings = 6 from VulnerableCryptoService:
        // 1 (CC-CRYPTO-001) + 1 (CC-CRYPTO-004) + 2 (CC-CRYPTO-002: DES & 3DES) + 1 (CC-CRYPTO-003) + 1 (CC-TLS-001)
        assertThat(stdout).contains("Security Findings: 6");
        assertThat(stdout).contains("Status: Findings detected. 6 security findings identified across 5 scanned files.");

        // Verify coverage-aware messaging
        assertThat(stdout).doesNotContain("Status: Clean");

        // Verify specific rules triggered
        assertThat(stdout).contains("CC-CRYPTO-001");
        assertThat(stdout).contains("CC-CRYPTO-002");
        assertThat(stdout).contains("CC-CRYPTO-003");
        assertThat(stdout).contains("CC-CRYPTO-004");
        assertThat(stdout).contains("CC-TLS-001");

        // Verify unresolved inventory observations are displayed
        assertThat(stdout).contains("Unresolved Cryptographic Usages (Inventory Observations)");
        assertThat(stdout).contains("dynamicMode");
        assertThat(stdout).contains("branchMode");
    }
}

