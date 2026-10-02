package com.codecontext.core.hierarchy;

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

class TypeHierarchyIndexTest {

    private SymbolRegistry symbolRegistry;
    private TypeHierarchyIndex hierarchyIndex;

    @BeforeEach
    void setUp() {
        symbolRegistry = new ConcurrentSymbolTable();
        hierarchyIndex = new TypeHierarchyIndex(symbolRegistry);
    }

    private TypeDefinition createType(String fqcn, String simpleName, String superClass, Set<String> interfaces, List<MethodDefinition> methods) {
        return new TypeDefinition(
                fqcn,
                simpleName,
                "com.example",
                TypeKind.CLASS,
                Path.of("src/" + simpleName + ".java"),
                Optional.ofNullable(superClass),
                interfaces,
                methods,
                List.of(),
                Set.of("public"),
                new SourceRange(1, 1, 50, 1)
        );
    }

    private MethodDefinition createMethod(String name, String returnType) {
        return new MethodDefinition(
                name,
                name + "()",
                returnType,
                List.of(),
                List.of(),
                Set.of("public"),
                false,
                new SourceRange(10, 5, 20, 5)
        );
    }

    @Test
    @DisplayName("UTC-S1.2-US01-001: Register single inheritance edge and query direct superclass")
    void testSingleInheritanceSuperclass() {
        TypeDefinition base = createType("com.example.BaseService", "BaseService", null, Set.of(), List.of());
        TypeDefinition sub = createType("com.example.SubService", "SubService", "com.example.BaseService", Set.of(), List.of());

        symbolRegistry.register(base);
        symbolRegistry.register(sub);
        hierarchyIndex.index(sub);

        Optional<String> directSuperclass = hierarchyIndex.findDirectSuperclass("com.example.SubService");
        assertThat(directSuperclass).contains("com.example.BaseService");
    }

    @Test
    @DisplayName("UTC-S1.2-US01-002: Register multiple interfaces and query direct interfaces")
    void testDirectInterfaces() {
        TypeDefinition entity = createType("com.example.UserEntity", "UserEntity", null, Set.of("com.example.Auditable", "java.io.Serializable"), List.of());

        symbolRegistry.register(entity);
        hierarchyIndex.index(entity);

        Set<String> interfaces = hierarchyIndex.findDirectInterfaces("com.example.UserEntity");
        assertThat(interfaces).containsExactlyInAnyOrder("com.example.Auditable", "java.io.Serializable");
    }

    @Test
    @DisplayName("UTC-S1.2-US01-003: Query transitive supertypes in topological order")
    void testTransitiveSupertypesTopological() {
        TypeDefinition a = createType("com.example.A", "A", null, Set.of(), List.of());
        TypeDefinition b = createType("com.example.B", "B", "com.example.A", Set.of(), List.of());
        TypeDefinition c = createType("com.example.C", "C", "com.example.B", Set.of(), List.of());

        symbolRegistry.register(a);
        symbolRegistry.register(b);
        symbolRegistry.register(c);

        hierarchyIndex.index(a);
        hierarchyIndex.index(b);
        hierarchyIndex.index(c);

        List<String> allSupertypes = hierarchyIndex.findAllSupertypes("com.example.C");
        assertThat(allSupertypes).containsExactly("com.example.B", "com.example.A");
    }

    @Test
    @DisplayName("UTC-S1.2-US01-004 & FTC-S1.2-001: Find method declared in direct superclass")
    void testFindMethodInDirectSuperclass() {
        MethodDefinition executeMethod = createMethod("execute", "void");
        TypeDefinition base = createType("com.example.BaseService", "BaseService", null, Set.of(), List.of(executeMethod));
        TypeDefinition sub = createType("com.example.SubService", "SubService", "com.example.BaseService", Set.of(), List.of());

        symbolRegistry.register(base);
        symbolRegistry.register(sub);
        hierarchyIndex.index(base);
        hierarchyIndex.index(sub);

        Optional<MethodDefinition> foundMethod = hierarchyIndex.findMethodInHierarchy("com.example.SubService", "execute");
        assertThat(foundMethod).isPresent();
        assertThat(foundMethod.get().name()).isEqualTo("execute");
    }

    @Test
    @DisplayName("UTC-S1.2-US01-005 & FTC-S1.2-003: Find method declared in transitive ancestor interface")
    void testFindMethodInAncestorInterface() {
        MethodDefinition findById = createMethod("findById", "Optional");
        TypeDefinition crudRepo = new TypeDefinition("com.example.CrudRepository", "CrudRepository", "com.example", TypeKind.INTERFACE,
                Path.of("src/CrudRepo.java"), Optional.empty(), Set.of(), List.of(findById), List.of(), Set.of("public"), new SourceRange(1, 1, 20, 1));
        TypeDefinition jpaRepo = new TypeDefinition("com.example.JpaRepository", "JpaRepository", "com.example", TypeKind.INTERFACE,
                Path.of("src/JpaRepo.java"), Optional.empty(), Set.of("com.example.CrudRepository"), List.of(), List.of(), Set.of("public"), new SourceRange(1, 1, 20, 1));
        TypeDefinition userRepo = new TypeDefinition("com.example.UserRepository", "UserRepository", "com.example", TypeKind.INTERFACE,
                Path.of("src/UserRepo.java"), Optional.empty(), Set.of("com.example.JpaRepository"), List.of(), List.of(), Set.of("public"), new SourceRange(1, 1, 20, 1));

        symbolRegistry.register(crudRepo);
        symbolRegistry.register(jpaRepo);
        symbolRegistry.register(userRepo);

        hierarchyIndex.index(crudRepo);
        hierarchyIndex.index(jpaRepo);
        hierarchyIndex.index(userRepo);

        Optional<MethodDefinition> found = hierarchyIndex.findMethodInHierarchy("com.example.UserRepository", "findById");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("findById");
    }

    @Test
    @DisplayName("UTC-S1.2-US01-006: Query non-existent method in hierarchy returns Optional.empty")
    void testQueryNonExistentMethod() {
        TypeDefinition base = createType("com.example.BaseService", "BaseService", null, Set.of(), List.of());
        symbolRegistry.register(base);
        hierarchyIndex.index(base);

        Optional<MethodDefinition> found = hierarchyIndex.findMethodInHierarchy("com.example.BaseService", "nonExistentMethod");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("UTC-S1.2-US01-007 & FTC-S1.2-006: Circular inheritance does not cause infinite recursion")
    void testCircularInheritanceCyclePrevention() {
        TypeDefinition a = createType("com.example.A", "A", "com.example.B", Set.of(), List.of());
        TypeDefinition b = createType("com.example.B", "B", "com.example.A", Set.of(), List.of());

        symbolRegistry.register(a);
        symbolRegistry.register(b);
        hierarchyIndex.index(a);
        hierarchyIndex.index(b);

        List<String> supertypes = hierarchyIndex.findAllSupertypes("com.example.A");
        assertThat(supertypes).containsExactly("com.example.B");

        Optional<MethodDefinition> method = hierarchyIndex.findMethodInHierarchy("com.example.A", "dummy");
        assertThat(method).isEmpty();
    }

    @Test
    @DisplayName("UTC-S1.2-US01-008: Self-inheritance cycle prevention terminates gracefully")
    void testSelfInheritanceCyclePrevention() {
        TypeDefinition a = createType("com.example.A", "A", "com.example.A", Set.of(), List.of());

        symbolRegistry.register(a);
        hierarchyIndex.index(a);

        List<String> supertypes = hierarchyIndex.findAllSupertypes("com.example.A");
        assertThat(supertypes).isEmpty();
    }

    @Test
    @DisplayName("UTC-S1.2-US01-009 & FTC-S1.2-007: Depth limit threshold (depth > 20) prevents stack overflow")
    void testDepthLimitThreshold() {
        for (int i = 1; i <= 25; i++) {
            String fqcn = "com.example.Class" + i;
            String superFqcn = (i > 1) ? ("com.example.Class" + (i - 1)) : null;
            TypeDefinition t = createType(fqcn, "Class" + i, superFqcn, Set.of(), List.of());
            symbolRegistry.register(t);
            hierarchyIndex.index(t);
        }

        List<String> supertypes = hierarchyIndex.findAllSupertypes("com.example.Class25");
        assertThat(supertypes).hasSize(20);
        assertThat(supertypes.get(0)).isEqualTo("com.example.Class24");
    }

    @Test
    @DisplayName("UTC-S1.2-US01-010 & FTC-S1.2-005: Overridden method returns most specific subtype definition")
    void testOverriddenMethodPrefersSubtype() {
        MethodDefinition parentFoo = createMethod("foo", "void");
        MethodDefinition childFoo = createMethod("foo", "void");

        TypeDefinition parent = createType("com.example.Parent", "Parent", null, Set.of(), List.of(parentFoo));
        TypeDefinition child = createType("com.example.Child", "Child", "com.example.Parent", Set.of(), List.of(childFoo));

        symbolRegistry.register(parent);
        symbolRegistry.register(child);
        hierarchyIndex.index(parent);
        hierarchyIndex.index(child);

        Optional<MethodDefinition> found = hierarchyIndex.findMethodInHierarchy("com.example.Child", "foo");
        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(childFoo);
    }
}