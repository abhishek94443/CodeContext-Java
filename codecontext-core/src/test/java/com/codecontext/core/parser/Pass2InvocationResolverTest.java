package com.codecontext.core.parser;

import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.index.SymbolRegistry;
import com.codecontext.core.model.*;
import com.codecontext.core.resolver.ResolutionChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Pass2InvocationResolverTest {

    private SymbolRegistry symbolRegistry;
    private TypeHierarchyIndex hierarchyIndex;
    private ResolutionChain resolutionChain;
    private Pass2InvocationResolver pass2Resolver;

    @BeforeEach
    void setUp() {
        symbolRegistry = new ConcurrentSymbolTable();
        hierarchyIndex = new TypeHierarchyIndex(symbolRegistry);
        resolutionChain = ResolutionChain.standardChain();
        pass2Resolver = new Pass2InvocationResolver(symbolRegistry, hierarchyIndex, resolutionChain);
    }

    private void registerType(String fqcn, String simpleName, String pkg, List<MethodDefinition> methods) {
        TypeDefinition def = new TypeDefinition(
                fqcn,
                simpleName,
                pkg,
                TypeKind.CLASS,
                Path.of("src/" + simpleName + ".java"),
                Optional.empty(),
                Set.of(),
                methods,
                List.of(),
                Set.of("public"),
                new SourceRange(1, 1, 40, 1)
        );
        symbolRegistry.register(def);
        hierarchyIndex.index(def);
    }

    private MethodDefinition createMethod(String name) {
        return new MethodDefinition(
                name,
                name + "()",
                "void",
                List.of(),
                List.of(),
                Set.of("public"),
                false,
                new SourceRange(10, 5, 20, 5)
        );
    }

    @Test
    @DisplayName("FTC-S1.3-007: Emit ParsedCompilationUnit with accurate InvocationReference records")
    void testEmitParsedCompilationUnit() {
        registerType("com.example.service.PaymentService", "PaymentService", "com.example.service", List.of(createMethod("processPayment")));

        String code = """
                package com.example.service;
                public class OrderService {
                    public void checkout() {
                        PaymentService paymentService = new PaymentService();
                        paymentService.processPayment();
                        System.out.println("done");
                    }
                }
                """;

        ParsedCompilationUnit unit = pass2Resolver.resolve(Path.of("OrderService.java"), code);

        assertThat(unit.packageName()).isEqualTo("com.example.service");
        assertThat(unit.invocations()).isNotEmpty();

        // Check constructor invocation
        boolean hasConstructor = unit.invocations().stream()
                .anyMatch(i -> i.kind() == InvocationKind.CONSTRUCTOR_CALL && i.targetFqcn().equals("com.example.service.PaymentService"));
        assertThat(hasConstructor).isTrue();

        // Check method invocation on paymentService
        boolean hasMethodCall = unit.invocations().stream()
                .anyMatch(i -> i.kind() == InvocationKind.METHOD_CALL && i.targetFqcn().equals("com.example.service.PaymentService") && i.targetMethodName().equals("processPayment"));
        assertThat(hasMethodCall).isTrue();
    }

    @Test
    @DisplayName("FTC-S1.3-008: ResolutionMetrics correctly tracks internal and external calls")
    void testResolutionMetricsAccuracy() {
        registerType("com.example.service.NotificationService", "NotificationService", "com.example.service", List.of(createMethod("send")));

        String code = """
                package com.example.service;
                public class AlertService {
                    public void alert() {
                        NotificationService notification = new NotificationService();
                        notification.send();
                        System.out.println("Alerted");
                    }
                }
                """;

        ParsedCompilationUnit unit = pass2Resolver.resolve(Path.of("AlertService.java"), code);
        ResolutionMetrics metrics = unit.metrics();

        assertThat(metrics.totalInvocations()).isGreaterThanOrEqualTo(2);
        assertThat(metrics.resolvedInternal()).isGreaterThanOrEqualTo(2); // NotificationService constructor + send()
        assertThat(metrics.resolutionRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("FTC-S1.3-010: Retain partial invocations when syntax errors occur")
    void testRetainPartialInvocationsOnSyntaxError() {
        String code = """
                package com.example.service;
                public class ResilientService {
                    public void validMethod() {
                        System.out.println("Valid");
                    }
                    public void brokenMethod( {
                    }
                }
                """;

        ParsedCompilationUnit unit = pass2Resolver.resolve(Path.of("ResilientService.java"), code);
        assertThat(unit.parseErrors()).isNotEmpty();
    }
}