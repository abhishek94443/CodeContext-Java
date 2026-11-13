package com.codecontext.core.security.crypto;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.security.rule.CompilationUnitContext;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoApiDetectorTest {

    private static final ParserConfiguration CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    private CompilationUnitContext parse(String code) {
        JavaParser parser = new JavaParser(CONFIG);
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        return new CompilationUnitContext(Path.of("Test.java"), code, cu, Optional.empty());
    }

    @Test
    @DisplayName("Detects FQCN javax.crypto.Cipher.getInstance")
    void testFqcnCipherDetection() {
        String code = """
                package com.example;
                public class App {
                    public void init() throws Exception {
                        javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).hasSize(1);
        CryptoApiUsage usage = usages.get(0);
        assertThat(usage.owner()).isEqualTo(CryptoApiOwner.CIPHER);
        assertThat(usage.isOwnerConfirmed()).isTrue();
        assertThat(usage.algorithm()).isPresent();
        assertThat(usage.algorithm().get().rawName()).isEqualTo("AES/GCM/NoPadding");
    }

    @Test
    @DisplayName("Detects Cipher.getInstance with explicit import")
    void testExplicitImportCipherDetection() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    public void init() throws Exception {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).hasSize(1);
        CryptoApiUsage usage = usages.get(0);
        assertThat(usage.owner()).isEqualTo(CryptoApiOwner.CIPHER);
        assertThat(usage.isOwnerConfirmed()).isTrue();
        assertThat(usage.algorithm().get().isEcbMode()).isTrue();
    }

    @Test
    @DisplayName("Detects Cipher.getInstance with wildcard import")
    void testWildcardImportCipherDetection() {
        String code = """
                package com.example;
                import javax.crypto.*;
                public class App {
                    public void init() throws Exception {
                        Cipher.getInstance("DES");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).owner()).isEqualTo(CryptoApiOwner.CIPHER);
        assertThat(usages.get(0).isOwnerConfirmed()).isTrue();
    }

    @Test
    @DisplayName("Detects static import getInstance")
    void testStaticImportDetection() {
        String code = """
                package com.example;
                import static java.security.MessageDigest.getInstance;
                public class App {
                    public void digest() throws Exception {
                        getInstance("SHA-256");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).owner()).isEqualTo(CryptoApiOwner.MESSAGE_DIGEST);
        assertThat(usages.get(0).algorithm().get().rawName()).isEqualTo("SHA-256");
    }

    @Test
    @DisplayName("Rejects FakeCipher.getInstance without mistaking for JCA")
    void testFakeCipherRejection() {
        String code = """
                package com.example;
                class FakeCipher {
                    static Object getInstance(String mode) { return null; }
                }
                public class App {
                    public void run() {
                        FakeCipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).isEmpty();
    }

    @Test
    @DisplayName("Rejects same-name custom class declared locally (class Cipher)")
    void testLocalClassShadowingRejection() {
        String code = """
                package com.example;
                class Cipher {
                    static Object getInstance(String mode) { return null; }
                }
                public class App {
                    public void run() {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        // Local class Cipher shadows javax.crypto.Cipher, must not be recognized as JCA
        assertThat(usages).isEmpty();
    }

    @Test
    @DisplayName("Rejects nested/inner class named Cipher without mistaking for JCA")
    void testNestedInnerClassCollisionRejection() {
        String code = """
                package com.example;
                import javax.crypto.*;
                public class Outer {
                    static class Cipher {
                        static Object getInstance(String mode) { return null; }
                    }
                    public void run() {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).isEmpty();
    }

    @Test
    @DisplayName("Downgrades confidence when conflicting non-standard wildcard imports exist")
    void testConflictingWildcardImportsDowngrade() {
        String code = """
                package com.example;
                import javax.crypto.*;
                import com.custom.crypto.*;
                public class App {
                    public void run() throws Exception {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).hasSize(1);
        // Ownership confidence must be downgraded, NOT confirmed
        assertThat(usages.get(0).ownerConfidence()).isEqualTo(AnalysisConfidence.LOW);
        assertThat(usages.get(0).isOwnerConfirmed()).isFalse();
    }

    @Test
    @DisplayName("Detects SecureRandom constructor instantiation")
    void testSecureRandomConstructorDetection() {
        String code = """
                package com.example;
                import java.security.SecureRandom;
                public class App {
                    public void run() {
                        SecureRandom rng = new SecureRandom();
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        List<CryptoApiUsage> usages = CryptoApiDetector.detectAll(ctx);

        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).owner()).isEqualTo(CryptoApiOwner.SECURE_RANDOM);
        assertThat(usages.get(0).methodName()).isEqualTo("<init>");
    }
}

