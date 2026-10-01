package com.codecontext.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeDefinitionTest {

    @Test
    @DisplayName("UTC-S1.1-US01-006: Null collections converted to empty unmodifiable collections")
    void shouldDefaultNullCollectionsToEmptyUnmodifiable() {
        TypeDefinition def = new TypeDefinition(
            "com.company.Service",
            "Service",
            "com.company",
            TypeKind.CLASS,
            Path.of("src/main/java/com/company/Service.java"),
            Optional.empty(),
            null,
            null,
            null,
            null,
            new SourceRange(1, 1, 10, 1)
        );

        assertThat(def.interfaces()).isNotNull().isEmpty();
        assertThat(def.methods()).isNotNull().isEmpty();
        assertThat(def.fields()).isNotNull().isEmpty();
        assertThat(def.annotations()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("UTC-S1.1-US01-007 / FTC-S1.1-003: Defensive copying and unmodifiable collections")
    void shouldDefensivelyCopyAndPreventMutation() {
        List<MethodDefinition> methods = new ArrayList<>();
        MethodDefinition method1 = new MethodDefinition(
            "execute",
            "execute()",
            "void",
            List.of(),
            List.of(),
            Set.of("public"),
            false,
            new SourceRange(5, 5, 8, 5)
        );
        methods.add(method1);

        TypeDefinition def = new TypeDefinition(
            "com.company.Service",
            "Service",
            "com.company",
            TypeKind.CLASS,
            Path.of("src/main/java/com/company/Service.java"),
            Optional.empty(),
            Set.of("Runnable"),
            methods,
            List.of(),
            Set.of("@Service"),
            new SourceRange(1, 1, 20, 1)
        );

        // Mutating original input list should not affect record
        MethodDefinition method2 = new MethodDefinition(
            "cancel",
            "cancel()",
            "void",
            List.of(),
            List.of(),
            Set.of("public"),
            false,
            new SourceRange(10, 5, 12, 5)
        );
        methods.add(method2);
        assertThat(def.methods()).hasSize(1);

        // Attempting to mutate returned collections directly should throw UnsupportedOperationException
        assertThatThrownBy(() -> def.methods().add(method2))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> def.interfaces().add("AutoCloseable"))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> def.annotations().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("UTC-S1.1-US01-008 / FTC-S1.1-005: Normalized path converts Windows backslashes to forward slashes")
    void shouldNormalizeWindowsBackslashPathToForwardSlashes() {
        TypeDefinition def = new TypeDefinition(
            "com.company.Service",
            "Service",
            "com.company",
            TypeKind.CLASS,
            Path.of("src\\main\\java\\com\\company\\Service.java"),
            Optional.empty(),
            Set.of(),
            List.of(),
            List.of(),
            Set.of(),
            new SourceRange(1, 1, 10, 1)
        );

        assertThat(def.normalizedPathString()).isEqualTo("src/main/java/com/company/Service.java");
        assertThat(def.normalizedPathString()).doesNotContain("\\");
    }
}
