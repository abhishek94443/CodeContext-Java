package com.codecontext.core.resolver;

import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.index.SymbolRegistry;
import com.codecontext.core.model.MethodDefinition;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.model.TypeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResolutionChainTest {

    private SymbolRegistry symbolRegistry;
    private TypeHierarchyIndex hierarchyIndex;
    private ResolutionChain chain;

    @BeforeEach
    void setUp() {
        symbolRegistry = new ConcurrentSymbolTable();
        hierarchyIndex = new TypeHierarchyIndex(symbolRegistry);
        chain = ResolutionChain.standardChain();
    }

    private TypeDefinition registerType(String fqcn, String simpleName, String pkg) {
        TypeDefinition def = new TypeDefinition(
                fqcn,
                simpleName,
                pkg,
                TypeKind.CLASS,
                Path.of("src/" + simpleName + ".java"),
                Optional.empty(),
                Set.of(),
                List.of(),
                List.of(),
                Set.of("public"),
                new SourceRange(1, 1, 30, 1)
        );
        symbolRegistry.register(def);
        hierarchyIndex.index(def);
        return def;
    }

    @Test
    @DisplayName("UTC-S1.3-US01-001: Same file resolution resolves locally declared inner class")
    void testSameFileResolution() {
        ResolutionContext ctx = new ResolutionContext(
                "Helper",
                "com.example.service",
                Set.of("com.example.service.OrderService", "com.example.service.OrderService$Helper"),
                List.of(),
                List.of(),
                symbolRegistry,
                hierarchyIndex
        );

        ResolutionResult result = chain.resolve(ctx);
        assertThat(result.resolvedFqcn()).isEqualTo("com.example.service.OrderService$Helper");
        assertThat(result.isResolvedLocally()).isTrue();
        assertThat(result.handlerName()).isEqualTo("SameFileHandler");
    }

    @Test
    @DisplayName("UTC-S1.3-US01-002 & FTC-S1.3-001: Same package resolution takes precedence without import")
    void testSamePackageResolution() {
        registerType("com.example.service.PaymentService", "PaymentService", "com.example.service");

        ResolutionContext ctx = new ResolutionContext(
                "PaymentService",
                "com.example.service",
                Set.of("com.example.service.OrderService"),
                List.of(),
                List.of(),
                symbolRegistry,
                hierarchyIndex
        );

        ResolutionResult result = chain.resolve(ctx);
        assertThat(result.resolvedFqcn()).isEqualTo("com.example.service.PaymentService");
        assertThat(result.isResolvedLocally()).isTrue();
        assertThat(result.handlerName()).isEqualTo("SamePackageHandler");
    }

    @Test
    @DisplayName("UTC-S1.3-US01-003 & FTC-S1.3-002: Explicit import resolution")
    void testExplicitImportResolution() {
        registerType("com.external.util.StringUtils", "StringUtils", "com.external.util");

        ResolutionContext ctx = new ResolutionContext(
                "StringUtils",
                "com.example.service",
                Set.of("com.example.service.OrderService"),
                List.of("com.external.util.StringUtils"),
                List.of(),
                symbolRegistry,
                hierarchyIndex
        );

        ResolutionResult result = chain.resolve(ctx);
        assertThat(result.resolvedFqcn()).isEqualTo("com.external.util.StringUtils");
        assertThat(result.isResolvedLocally()).isTrue();
        assertThat(result.handlerName()).isEqualTo("ExplicitImportHandler");
    }

    @Test
    @DisplayName("UTC-S1.3-US01-004 & FTC-S1.3-004: Wildcard import resolution when class exists in package")
    void testWildcardImportResolution() {
        registerType("com.example.dao.UserDao", "UserDao", "com.example.dao");

        ResolutionContext ctx = new ResolutionContext(
                "UserDao",
                "com.example.service",
                Set.of("com.example.service.OrderService"),
                List.of(),
                List.of("com.example.dao.*"),
                symbolRegistry,
                hierarchyIndex
        );

        ResolutionResult result = chain.resolve(ctx);
        assertThat(result.resolvedFqcn()).isEqualTo("com.example.dao.UserDao");
        assertThat(result.isResolvedLocally()).isTrue();
        assertThat(result.handlerName()).isEqualTo("WildcardImportHandler");
    }

    @Test
    @DisplayName("UTC-S1.3-US01-006 & FTC-S1.3-003: Precedence: Same package takes precedence over wildcard collision")
    void testSamePackageOverWildcardPrecedence() {
        // Both com.example.service and com.other.util declare Config
        registerType("com.example.service.Config", "Config", "com.example.service");
        registerType("com.other.util.Config", "Config", "com.other.util");

        ResolutionContext ctx = new ResolutionContext(
                "Config",
                "com.example.service",
                Set.of(),
                List.of(),
                List.of("com.other.util.*"),
                symbolRegistry,
                hierarchyIndex
        );

        ResolutionResult result = chain.resolve(ctx);
        assertThat(result.resolvedFqcn()).isEqualTo("com.example.service.Config");
        assertThat(result.handlerName()).isEqualTo("SamePackageHandler");
    }

    @Test
    @DisplayName("UTC-S1.3-US01-008 & FTC-S1.3-006: External fallback resolves standard java.lang type")
    void testExternalFallbackJavaLang() {
        ResolutionContext ctx = new ResolutionContext(
                "String",
                "com.example.service",
                Set.of(),
                List.of(),
                List.of(),
                symbolRegistry,
                hierarchyIndex
        );

        ResolutionResult result = chain.resolve(ctx);
        assertThat(result.resolvedFqcn()).isEqualTo("java.lang.String");
        assertThat(result.isResolvedLocally()).isFalse();
        assertThat(result.handlerName()).isEqualTo("ExternalFallbackHandler");
    }

    @Test
    @DisplayName("UTC-S1.3-US01-009: External fallback handles unknown third-party library")
    void testExternalFallbackUnknownThirdParty() {
        ResolutionContext ctx = new ResolutionContext(
                "org.slf4j.Logger",
                "com.example.service",
                Set.of(),
                List.of(),
                List.of(),
                symbolRegistry,
                hierarchyIndex
        );

        ResolutionResult result = chain.resolve(ctx);
        assertThat(result.resolvedFqcn()).isEqualTo("org.slf4j.Logger");
        assertThat(result.isResolvedLocally()).isFalse();
        assertThat(result.handlerName()).isEqualTo("ExternalFallbackHandler");
    }
}