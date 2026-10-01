package com.codecontext.core.index;

import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.model.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentSymbolTableTest {

    private TypeDefinition createSampleType(String packageName, String simpleName) {
        String fqcn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        return new TypeDefinition(
            fqcn,
            simpleName,
            packageName,
            TypeKind.CLASS,
            Path.of("src/main/java/" + fqcn.replace('.', '/') + ".java"),
            Optional.empty(),
            Set.of(),
            List.of(),
            List.of(),
            Set.of(),
            new SourceRange(1, 1, 10, 1)
        );
    }

    @Test
    @DisplayName("UTC-S1.1-US02-001 / FTC-S1.1-006: Register single type and verify all 3 index lookups")
    void shouldRegisterAndRetrieveAcrossAllThreeIndices() {
        ConcurrentSymbolTable table = new ConcurrentSymbolTable();
        TypeDefinition userType = createSampleType("com.company.models", "User");
        TypeDefinition orderType = createSampleType("com.company.models", "Order");

        table.register(userType);
        table.register(orderType);

        // 1. FQCN lookup
        Optional<TypeDefinition> fqcnResult = table.findByFqcn("com.company.models.User");
        assertThat(fqcnResult).isPresent().contains(userType);

        // 2. Package lookup
        Set<TypeDefinition> packageResult = table.findByPackage("com.company.models");
        assertThat(packageResult).containsExactlyInAnyOrder(userType, orderType);

        // 3. Simple name lookup
        Set<TypeDefinition> simpleResult = table.findBySimpleName("Order");
        assertThat(simpleResult).containsExactly(orderType);

        assertThat(table.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("FTC-S1.1-007: Return Optional.empty for non-existent FQCN query")
    void shouldReturnEmptyOptionalForUnknownFqcn() {
        SymbolLookup lookup = new ConcurrentSymbolTable();

        Optional<TypeDefinition> result = lookup.findByFqcn("com.company.unknown.NonExistentService");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("UTC-S1.1-US02-002: Package and SimpleName lookups return unmodifiable views")
    void shouldReturnUnmodifiableViewsForCollections() {
        ConcurrentSymbolTable table = new ConcurrentSymbolTable();
        TypeDefinition type = createSampleType("com.pkg", "Foo");
        table.register(type);

        Set<TypeDefinition> byPkg = table.findByPackage("com.pkg");
        TypeDefinition dummy = createSampleType("com.pkg", "Bar");
        assertThatThrownBy(() -> byPkg.add(dummy))
            .isInstanceOf(UnsupportedOperationException.class);

        Set<TypeDefinition> byName = table.findBySimpleName("Foo");
        assertThatThrownBy(() -> byName.add(dummy))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("UTC-S1.1-US02-003: Multiple classes with identical simple name in different packages")
    void shouldHandleSimpleNameCollisionsGracefully() {
        ConcurrentSymbolTable table = new ConcurrentSymbolTable();
        TypeDefinition serviceA = createSampleType("com.service.a", "PaymentService");
        TypeDefinition serviceB = createSampleType("com.service.b", "PaymentService");

        table.register(serviceA);
        table.register(serviceB);

        Set<TypeDefinition> found = table.findBySimpleName("PaymentService");
        assertThat(found).hasSize(2).containsExactlyInAnyOrder(serviceA, serviceB);
    }

    @Test
    @DisplayName("UTC-S1.1-US02-004: Duplicate registration replaces entry cleanly")
    void shouldReplaceExistingEntryOnDuplicateRegistration() {
        ConcurrentSymbolTable table = new ConcurrentSymbolTable();
        TypeDefinition v1 = createSampleType("com.service", "PaymentService");
        table.register(v1);

        // Create updated definition for same FQCN (e.g. edited file)
        TypeDefinition v2 = new TypeDefinition(
            "com.service.PaymentService",
            "PaymentService",
            "com.service",
            TypeKind.RECORD,
            Path.of("src/main/java/com/service/PaymentService.java"),
            Optional.empty(),
            Set.of("Serializable"),
            List.of(),
            List.of(),
            Set.of("@Component"),
            new SourceRange(1, 1, 25, 1)
        );
        table.register(v2);

        assertThat(table.size()).isEqualTo(1);
        assertThat(table.findByFqcn("com.service.PaymentService")).isPresent().contains(v2);
        assertThat(table.findByPackage("com.service")).containsExactly(v2);
        assertThat(table.findBySimpleName("PaymentService")).containsExactly(v2);
    }

    @Test
    @DisplayName("UTC-S1.1-US02-005: Clear resets all indices")
    void shouldResetAllIndicesOnClear() {
        ConcurrentSymbolTable table = new ConcurrentSymbolTable();
        table.register(createSampleType("com.pkg", "A"));
        table.register(createSampleType("com.pkg", "B"));
        assertThat(table.size()).isEqualTo(2);

        table.clear();

        assertThat(table.size()).isEqualTo(0);
        assertThat(table.findByFqcn("com.pkg.A")).isEmpty();
        assertThat(table.findByPackage("com.pkg")).isEmpty();
        assertThat(table.findBySimpleName("A")).isEmpty();
    }

    @Test
    @DisplayName("FTC-S1.1-008: Interface Segregation Principle compile-time contract check")
    void shouldEnforceInterfaceSegregationContract() {
        SymbolRegistry registry = new ConcurrentSymbolTable();
        TypeDefinition type = createSampleType("com.api", "OrderApi");
        registry.register(type);

        // When viewed via read-only SymbolLookup interface
        SymbolLookup readOnlyLookup = registry;

        assertThat(readOnlyLookup.findByFqcn("com.api.OrderApi")).isPresent().contains(type);
        assertThat(readOnlyLookup.findByPackage("com.api")).containsExactly(type);
        assertThat(readOnlyLookup.findBySimpleName("OrderApi")).containsExactly(type);
    }

    @Test
    @DisplayName("FTC-S1.1-009: Concurrent multi-threaded read and write safety with Virtual Threads")
    void shouldHandleHighConcurrencyVirtualThreadsSafely() throws Exception {
        ConcurrentSymbolTable table = new ConcurrentSymbolTable();
        int threadCount = 50;
        int typesPerThread = 100; // 50 * 100 = 5,000 total types

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                tasks.add(() -> {
                    for (int i = 0; i < typesPerThread; i++) {
                        String pkg = "com.company.module" + (threadId % 10);
                        String name = "Service_" + threadId + "_" + i;
                        TypeDefinition typeDef = createSampleType(pkg, name);

                        // Concurrent registration
                        table.register(typeDef);

                        // Concurrent random query
                        table.findByFqcn(typeDef.fqcn());
                        table.findByPackage(pkg);
                        table.findBySimpleName(name);
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(); // Assert no exceptions in any virtual thread
            }
        }

        assertThat(table.size()).isEqualTo(threadCount * typesPerThread);
    }
}