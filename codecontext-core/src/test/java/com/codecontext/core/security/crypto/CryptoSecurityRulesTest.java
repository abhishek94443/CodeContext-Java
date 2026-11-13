package com.codecontext.core.security.crypto;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.security.rule.CompilationUnitContext;
import com.codecontext.core.security.rule.EcbModeSecurityRule;
import com.codecontext.core.security.rule.LegacyCipherSecurityRule;
import com.codecontext.core.security.rule.ObsoleteTlsSecurityRule;
import com.codecontext.core.security.rule.WeakDigestSecurityRule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoSecurityRulesTest {

    private static final ParserConfiguration CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    private CompilationUnitContext parse(String code) {
        JavaParser parser = new JavaParser(CONFIG);
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        return new CompilationUnitContext(Path.of("CryptoTest.java"), code, cu, Optional.empty());
    }

    // -------------------------------------------------------------
    // CC-CRYPTO-001: Insecure ECB Mode
    // -------------------------------------------------------------

    @Test
    @DisplayName("CC-CRYPTO-001: Flags direct AES/ECB string literal with HIGH severity")
    void testDirectAesEcbLiteral() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        EcbModeSecurityRule rule = new EcbModeSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("CC-CRYPTO-001");
        assertThat(f.severity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(f.message()).contains("ECB");
        assertThat(f.location().orElseThrow().startLine()).isEqualTo(5);
    }

    @Test
    @DisplayName("CC-CRYPTO-001: Does not flag AES/GCM authenticated encryption")
    void testAesGcmNoEcbFinding() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("AES/GCM/NoPadding");
                    }
                }
                """;
        EcbModeSecurityRule rule = new EcbModeSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("CC-CRYPTO-001: Flags ECB mode constructed via string concatenation")
    void testEcbConcatenation() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("AES/" + "ECB/PKCS5Padding");
                    }
                }
                """;
        EcbModeSecurityRule rule = new EcbModeSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("CC-CRYPTO-001");
    }

    // -------------------------------------------------------------
    // CC-CRYPTO-002: Legacy Insecure Cipher
    // -------------------------------------------------------------

    @Test
    @DisplayName("CC-CRYPTO-002: Flags DES cipher with HIGH severity")
    void testDesCipherDetection() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("DES/CBC/PKCS5Padding");
                    }
                }
                """;
        LegacyCipherSecurityRule rule = new LegacyCipherSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("CC-CRYPTO-002");
        assertThat(f.severity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(f.message()).contains("DES");
    }

    @Test
    @DisplayName("CC-CRYPTO-002: Flags RC4/ARCFOUR cipher with HIGH severity")
    void testRc4CipherDetection() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("RC4");
                    }
                }
                """;
        LegacyCipherSecurityRule rule = new LegacyCipherSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("CC-CRYPTO-002");
        assertThat(f.severity()).isEqualTo(FindingSeverity.HIGH);
    }

    @Test
    @DisplayName("CC-CRYPTO-002: Flags 3DES/DESede with MEDIUM severity and deprecation guidance")
    void testTripleDesNuancedSeverity() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("DESede/CBC/PKCS5Padding");
                    }
                }
                """;
        LegacyCipherSecurityRule rule = new LegacyCipherSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("CC-CRYPTO-002");
        assertThat(f.severity()).isEqualTo(FindingSeverity.MEDIUM);
        assertThat(f.message()).contains("Triple DES");
    }

    // -------------------------------------------------------------
    // CC-CRYPTO-003: Weak Digest Usage
    // -------------------------------------------------------------

    @Test
    @DisplayName("CC-CRYPTO-003: Discovers MD5 digest with MEDIUM severity review classification")
    void testMd5WeakDigest() {
        String code = """
                package com.example;
                import java.security.MessageDigest;
                public class App {
                    void test() throws Exception {
                        MessageDigest.getInstance("MD5");
                    }
                }
                """;
        WeakDigestSecurityRule rule = new WeakDigestSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("CC-CRYPTO-003");
        assertThat(f.severity()).isEqualTo(FindingSeverity.MEDIUM);
        assertThat(f.message()).contains("MD5");
    }

    @Test
    @DisplayName("CC-CRYPTO-003: Discovers SHA-1 digest")
    void testSha1WeakDigest() {
        String code = """
                package com.example;
                import java.security.MessageDigest;
                public class App {
                    void test() throws Exception {
                        MessageDigest.getInstance("SHA-1");
                    }
                }
                """;
        WeakDigestSecurityRule rule = new WeakDigestSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("CC-CRYPTO-003");
    }

    @Test
    @DisplayName("CC-CRYPTO-003: Does not flag SHA-256 (clean inventory only)")
    void testSha256Clean() {
        String code = """
                package com.example;
                import java.security.MessageDigest;
                public class App {
                    void test() throws Exception {
                        MessageDigest.getInstance("SHA-256");
                    }
                }
                """;
        WeakDigestSecurityRule rule = new WeakDigestSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).isEmpty();
    }

    // -------------------------------------------------------------
    // CC-TLS-001: Obsolete TLS Protocol Selection
    // -------------------------------------------------------------

    @Test
    @DisplayName("CC-TLS-001: Flags explicit TLSv1 with HIGH severity")
    void testTlsV1Obsolete() {
        String code = """
                package com.example;
                import javax.net.ssl.SSLContext;
                public class App {
                    void test() throws Exception {
                        SSLContext.getInstance("TLSv1");
                    }
                }
                """;
        ObsoleteTlsSecurityRule rule = new ObsoleteTlsSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("CC-TLS-001");
        assertThat(f.severity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(f.message()).contains("TLSv1");
    }

    @Test
    @DisplayName("CC-TLS-001: Flags explicit TLSv1.1 with HIGH severity")
    void testTlsV11Obsolete() {
        String code = """
                package com.example;
                import javax.net.ssl.SSLContext;
                public class App {
                    void test() throws Exception {
                        SSLContext.getInstance("TLSv1.1");
                    }
                }
                """;
        ObsoleteTlsSecurityRule rule = new ObsoleteTlsSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("CC-TLS-001");
    }

    @Test
    @DisplayName("CC-TLS-001: Flags explicit SSLv3 with HIGH severity")
    void testSslV3Obsolete() {
        String code = """
                package com.example;
                import javax.net.ssl.SSLContext;
                public class App {
                    void test() throws Exception {
                        SSLContext.getInstance("SSLv3");
                    }
                }
                """;
        ObsoleteTlsSecurityRule rule = new ObsoleteTlsSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("CC-TLS-001");
    }

    @Test
    @DisplayName("CC-TLS-001: Does not flag TLSv1.3 or generic TLS")
    void testModernTlsNotFlagged() {
        String code = """
                package com.example;
                import javax.net.ssl.SSLContext;
                public class App {
                    void test() throws Exception {
                        SSLContext.getInstance("TLSv1.3");
                        SSLContext.getInstance("TLS");
                    }
                }
                """;
        ObsoleteTlsSecurityRule rule = new ObsoleteTlsSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).isEmpty();
    }

    // -------------------------------------------------------------
    // CC-CRYPTO-004: Provider-Dependent Default Cipher Transformation
    // -------------------------------------------------------------

    @Test
    @DisplayName("CC-CRYPTO-004: Flags bare Cipher.getInstance(\"AES\") with MEDIUM severity warning")
    void testBareCipherTransformation() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("AES");
                    }
                }
                """;
        com.codecontext.core.security.rule.ProviderDependentCipherSecurityRule rule =
                new com.codecontext.core.security.rule.ProviderDependentCipherSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("CC-CRYPTO-004");
        assertThat(f.severity()).isEqualTo(FindingSeverity.MEDIUM);
        assertThat(f.message()).contains("provider");
    }

    @Test
    @DisplayName("CC-CRYPTO-004: Does not flag fully-specified transformation (e.g. AES/GCM/NoPadding)")
    void testFullySpecifiedCipherNoBareWarning() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("AES/GCM/NoPadding");
                    }
                }
                """;
        com.codecontext.core.security.rule.ProviderDependentCipherSecurityRule rule =
                new com.codecontext.core.security.rule.ProviderDependentCipherSecurityRule();
        List<Finding> findings = rule.analyze(parse(code));

        assertThat(findings).isEmpty();
    }
}

