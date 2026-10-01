package com.codecontext.core.parser;

import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.model.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedPass1ScannerTest {

    @Test
    @DisplayName("FTC-S1.1-011: Complete Pass 1 scan of directory tree with classes, records, interfaces, and enums")
    void shouldScanAndCatalogAllTypesInDirectoryTree(@TempDir Path tempDir) throws IOException {
        Path serviceDir = Files.createDirectories(tempDir.resolve("src/main/java/com/company/service"));
        Path dtoDir = Files.createDirectories(tempDir.resolve("src/main/java/com/company/dto"));
        Path apiDir = Files.createDirectories(tempDir.resolve("src/main/java/com/company/api"));

        Files.writeString(serviceDir.resolve("OrderService.java"), """
            package com.company.service;
            public class OrderService {
                public void process() {}
            }
            """);

        Files.writeString(dtoDir.resolve("OrderDto.java"), """
            package com.company.dto;
            public record OrderDto(String id) {}
            """);

        Files.writeString(apiDir.resolve("OrderApi.java"), """
            package com.company.api;
            public interface OrderApi {
                void execute();
            }
            """);

        Files.writeString(apiDir.resolve("OrderStatus.java"), """
            package com.company.api;
            public enum OrderStatus {
                ACTIVE, INACTIVE
            }
            """);

        ConcurrentSymbolTable registry = new ConcurrentSymbolTable();
        BoundedPass1Scanner scanner = new BoundedPass1Scanner();

        ScanSummary summary = scanner.scan(tempDir, registry);

        assertThat(summary.totalFilesScanned()).isEqualTo(4);
        assertThat(summary.totalTypesDiscovered()).isEqualTo(4);
        assertThat(summary.failedFiles()).isEmpty();

        // Verify all 4 types exist in registry
        assertThat(registry.findByFqcn("com.company.service.OrderService")).isPresent();
        assertThat(registry.findByFqcn("com.company.dto.OrderDto")).isPresent();
        assertThat(registry.findByFqcn("com.company.api.OrderApi")).isPresent();
        assertThat(registry.findByFqcn("com.company.api.OrderStatus")).isPresent();
    }
}