package com.codecontext.core.parser;

import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.model.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserDeclarationVisitorTest {

    private final JavaParserDeclarationVisitor visitor = new JavaParserDeclarationVisitor();

    @Test
    @DisplayName("UTC-S1.1-US03-005: Extracts standard class with methods and fields")
    void shouldExtractClassWithMethodsAndFields() {
        String code = """
            package com.company.billing;

            import java.util.List;

            public class InvoiceService {
                private String currency;
                public static final int MAX_RETRY = 3;

                public void generateInvoice(String customerId) {
                }

                private boolean isValid() {
                    return true;
                }
            }
            """;

        ConcurrentSymbolTable registry = new ConcurrentSymbolTable();
        visitor.parseAndRegister(code, Path.of("InvoiceService.java"), registry);

        Optional<TypeDefinition> defOpt = registry.findByFqcn("com.company.billing.InvoiceService");
        assertThat(defOpt).isPresent();

        TypeDefinition def = defOpt.get();
        assertThat(def.kind()).isEqualTo(TypeKind.CLASS);
        assertThat(def.packageName()).isEqualTo("com.company.billing");
        assertThat(def.simpleName()).isEqualTo("InvoiceService");

        // Verify fields
        assertThat(def.fields()).hasSize(2);
        assertThat(def.fields()).extracting("name").containsExactlyInAnyOrder("currency", "MAX_RETRY");

        // Verify methods
        assertThat(def.methods()).hasSize(2);
        assertThat(def.methods()).extracting("name").containsExactlyInAnyOrder("generateInvoice", "isValid");
    }

    @Test
    @DisplayName("UTC-S1.1-US03-006: Extracts Java 21 Record with components")
    void shouldExtractJava21Record() {
        String code = """
            package com.company.dto;

            public record OrderDto(String orderId, double amount) {
                public boolean isFree() {
                    return amount == 0.0;
                }
            }
            """;

        ConcurrentSymbolTable registry = new ConcurrentSymbolTable();
        visitor.parseAndRegister(code, Path.of("OrderDto.java"), registry);

        Optional<TypeDefinition> defOpt = registry.findByFqcn("com.company.dto.OrderDto");
        assertThat(defOpt).isPresent();

        TypeDefinition def = defOpt.get();
        assertThat(def.kind()).isEqualTo(TypeKind.RECORD);
        assertThat(def.fields()).extracting("name").containsExactlyInAnyOrder("orderId", "amount");
        assertThat(def.methods()).extracting("name").contains("isFree");
    }

    @Test
    @DisplayName("UTC-S1.1-US03-007: Extracts Enum with constants and methods")
    void shouldExtractEnum() {
        String code = """
            package com.company.enums;

            public enum OrderStatus {
                PENDING,
                SHIPPED,
                DELIVERED;

                public boolean isTerminal() {
                    return this == DELIVERED;
                }
            }
            """;

        ConcurrentSymbolTable registry = new ConcurrentSymbolTable();
        visitor.parseAndRegister(code, Path.of("OrderStatus.java"), registry);

        Optional<TypeDefinition> defOpt = registry.findByFqcn("com.company.enums.OrderStatus");
        assertThat(defOpt).isPresent();

        TypeDefinition def = defOpt.get();
        assertThat(def.kind()).isEqualTo(TypeKind.ENUM);
        assertThat(def.methods()).extracting("name").contains("isTerminal");
    }

    @Test
    @DisplayName("UTC-S1.1-US03-008: Handles static nested class with $ delimiter")
    void shouldExtractStaticNestedClass() {
        String code = """
            package com.company.nested;

            public class Outer {
                public static class Inner {
                    public void innerMethod() {}
                }
            }
            """;

        ConcurrentSymbolTable registry = new ConcurrentSymbolTable();
        visitor.parseAndRegister(code, Path.of("Outer.java"), registry);

        assertThat(registry.findByFqcn("com.company.nested.Outer")).isPresent();
        assertThat(registry.findByFqcn("com.company.nested.Outer$Inner")).isPresent();
    }

    @Test
    @DisplayName("UTC-S1.1-US03-009 / FTC-S1.1-012: Syntax error records parse error without crashing")
    void shouldHandleSyntaxErrorGracefully() {
        String brokenCode = """
            package com.company.broken;

            public class BrokenClass {
                public void unclosed( {
            """;

        ConcurrentSymbolTable registry = new ConcurrentSymbolTable();
        ParseResult result = visitor.parseAndRegister(brokenCode, Path.of("BrokenClass.java"), registry);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).isNotEmpty();
    }
}