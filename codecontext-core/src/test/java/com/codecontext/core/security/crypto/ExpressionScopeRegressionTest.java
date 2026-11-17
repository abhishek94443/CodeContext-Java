package com.codecontext.core.security.crypto;

import com.codecontext.core.security.rule.CompilationUnitContext;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class ExpressionScopeRegressionTest {
    private ExpressionResolution resolve(String source) {
        var cu = StaticJavaParser.parse(source);
        var call = cu.findAll(MethodCallExpr.class).stream()
                .filter(c -> c.getNameAsString().equals("getInstance")).findFirst().orElseThrow();
        return LimitedExpressionResolver.resolve(call.getArgument(0),
                new CompilationUnitContext(Path.of("Example.java"), source, cu, Optional.empty()), call);
    }

    @Test void parameterShadowsField() {
        assertThat(resolve("class A { static final String alg=\"MD5\"; void f(String alg) { Digest.getInstance(alg); } }").status())
                .isEqualTo(ResolutionStatus.UNKNOWN);
    }
    @Test void siblingBlockDoesNotLeak() {
        assertThat(resolve("class A { void f() { { String alg=\"MD5\"; } Digest.getInstance(alg); } }").status())
                .isEqualTo(ResolutionStatus.UNKNOWN);
    }
    @Test void enclosingBlockIsVisible() {
        assertThat(resolve("class A { void f() { String alg=\"SHA-256\"; { Digest.getInstance(alg); } } }").resolvedValue())
                .contains("SHA-256");
    }
    @Test void unrelatedNestedFieldDoesNotLeak() {
        assertThat(resolve("class A { class B { final String alg=\"MD5\"; } void f() { Digest.getInstance(alg); } }").status())
                .isEqualTo(ResolutionStatus.UNKNOWN);
    }
    @Test void qualifiedConstantUsesOwner() {
        assertThat(resolve("class A { static final String ALG=\"MD5\"; void f() { Digest.getInstance(B.ALG); } } class B { static final String ALG=\"SHA-256\"; }").resolvedValue())
                .contains("SHA-256");
    }
    @Test void unknownQualifierDoesNotBorrowField() {
        assertThat(resolve("class A { static final String ALG=\"MD5\"; void f() { Digest.getInstance(Missing.ALG); } }").status())
                .isEqualTo(ResolutionStatus.UNKNOWN);
    }
    @Test void compoundAssignmentIsNotSimpleReplacement() {
        assertThat(resolve("class A { void f() { String alg=\"SHA-\"; alg += \"256\"; Digest.getInstance(alg); } }").status())
                .isEqualTo(ResolutionStatus.UNKNOWN);
    }
    @Test void simpleAssignmentStillResolves() {
        assertThat(resolve("class A { void f() { String alg=\"MD5\"; alg = \"SHA-256\"; Digest.getInstance(alg); } }").resolvedValue())
                .contains("SHA-256");
    }
}
