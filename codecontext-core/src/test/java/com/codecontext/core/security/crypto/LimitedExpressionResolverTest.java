package com.codecontext.core.security.crypto;

import com.codecontext.core.security.rule.CompilationUnitContext;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LimitedExpressionResolverTest {

    private static final ParserConfiguration CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    private CompilationUnitContext parse(String code) {
        JavaParser parser = new JavaParser(CONFIG);
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        return new CompilationUnitContext(Path.of("Test.java"), code, cu, Optional.empty());
    }

    @Test
    @DisplayName("Resolves direct string literal")
    void testDirectStringLiteral() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("AES/ECB/PKCS5Padding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(res.resolvedValue()).contains("AES/ECB/PKCS5Padding");
    }

    @Test
    @DisplayName("Resolves constant string concatenation")
    void testStringConcatenation() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance("AES/" + "ECB/PKCS5Padding");
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(res.resolvedValue()).contains("AES/ECB/PKCS5Padding");
    }

    @Test
    @DisplayName("Resolves final local variable")
    void testFinalLocalVariable() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        final String mode = "AES/ECB/PKCS5Padding";
                        Cipher.getInstance(mode);
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(res.resolvedValue()).contains("AES/ECB/PKCS5Padding");
    }

    @Test
    @DisplayName("Resolves simple non-final local variable before call")
    void testSimpleLocalAssignment() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        String mode = "AES/GCM/NoPadding";
                        Cipher.getInstance(mode);
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(res.resolvedValue()).contains("AES/GCM/NoPadding");
    }

    @Test
    @DisplayName("Resolves static final class constant")
    void testStaticFinalConstant() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    private static final String CIPHER_MODE = "AES/ECB/PKCS5Padding";
                    void test() throws Exception {
                        Cipher.getInstance(CIPHER_MODE);
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(res.resolvedValue()).contains("AES/ECB/PKCS5Padding");
    }

    @Test
    @DisplayName("Does NOT use variable assigned after the call site")
    void testVariableAssignedAfterCall() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test() throws Exception {
                        Cipher.getInstance(mode);
                        String mode = "AES/ECB/PKCS5Padding";
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isNotEqualTo(ResolutionStatus.RESOLVED);
        assertThat(res.resolvedValue()).isEmpty();
    }

    @Test
    @DisplayName("Respects shadowed local variable in nested block")
    void testShadowedLocalVariable() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    private static final String MODE = "DES";
                    void test() throws Exception {
                        String MODE = "AES/GCM/NoPadding";
                        Cipher.getInstance(MODE);
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(res.resolvedValue()).contains("AES/GCM/NoPadding");
    }

    @Test
    @DisplayName("Treats variable reassigned across conditional branch as AMBIGUOUS")
    void testReassignmentInConditionalBranch() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test(boolean condition) throws Exception {
                        String mode = "AES/GCM/NoPadding";
                        if (condition) {
                            mode = "AES/ECB/PKCS5Padding";
                        }
                        Cipher.getInstance(mode);
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.AMBIGUOUS);
        assertThat(res.resolvedValue()).isEmpty();
    }

    @Test
    @DisplayName("Treats dynamic method call (e.g. config.getMode()) as UNKNOWN")
    void testDynamicMethodCallUnknown() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    void test(Config config) throws Exception {
                        String mode = config.getMode();
                        Cipher.getInstance(mode);
                    }
                }
                class Config { String getMode() { return "AES/GCM"; } }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.UNKNOWN);
        assertThat(res.resolvedValue()).isEmpty();
    }

    @Test
    @DisplayName("Treats non-final mutable class field as UNKNOWN")
    void testNonFinalFieldUnknown() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    private String mode = "AES/ECB/PKCS5Padding";
                    void test() throws Exception {
                        Cipher.getInstance(mode);
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.UNKNOWN);
    }

    @Test
    @DisplayName("Detects circular constant references without infinite recursion")
    void testCircularConstantReferences() {
        String code = """
                package com.example;
                import javax.crypto.Cipher;
                public class App {
                    private static final String A = B;
                    private static final String B = A;
                    void test() throws Exception {
                        Cipher.getInstance(A);
                    }
                }
                """;
        CompilationUnitContext ctx = parse(code);
        MethodCallExpr call = ctx.compilationUnit().findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .findFirst().orElseThrow();
        ExpressionResolution res = LimitedExpressionResolver.resolve(call.getArgument(0), ctx, call);

        assertThat(res.status()).isEqualTo(ResolutionStatus.AMBIGUOUS);
    }
}
