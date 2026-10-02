package com.codecontext.core.stress;

import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.model.ParsedCompilationUnit;
import com.codecontext.core.parser.BoundedPass1Scanner;
import com.codecontext.core.parser.Pass2InvocationResolver;
import com.codecontext.core.parser.ScanSummary;
import com.codecontext.core.resolver.ResolutionChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Phase1StressVerificationTest {

    @Test
    @DisplayName("FTC-S1.3-011 & FTC-S1.3-012: Stress test 500 classes under bounded heap (< 512MB) and verify throughput")
    void testPhase1StressAndMemoryCeiling(@TempDir Path tempDir) throws IOException {
        int packageCount = 10;
        int classesPerPackage = 50; // Total 500 classes with cross-package invocations
        int totalClasses = packageCount * classesPerPackage;

        // Generate synthetic repository
        for (int p = 0; p < packageCount; p++) {
            Path pkgDir = tempDir.resolve("com/example/pkg" + p);
            Files.createDirectories(pkgDir);

            for (int c = 0; c < classesPerPackage; c++) {
                String className = "Service" + c;
                int nextPkg = (p + 1) % packageCount;
                int nextClass = (c + 1) % classesPerPackage;

                String code = """
                        package com.example.pkg%d;
                        import com.example.pkg%d.Service%d;
                        
                        public class %s {
                            public void execute() {
                                Service%d next = new Service%d();
                                next.execute();
                            }
                        }
                        """.formatted(p, nextPkg, nextClass, className, nextClass, nextClass);

                Files.writeString(pkgDir.resolve(className + ".java"), code);
            }
        }

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        System.gc();
        long initialMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long startTime = System.currentTimeMillis();

        // 1. Pass 1 Scan
        ConcurrentSymbolTable symbolTable = new ConcurrentSymbolTable();
        BoundedPass1Scanner scanner = new BoundedPass1Scanner();
        ScanSummary summary = scanner.scan(tempDir, symbolTable);

        long pass1Duration = System.currentTimeMillis() - startTime;
        assertThat(summary.totalFilesScanned()).isEqualTo(totalClasses);
        assertThat(summary.failedFiles()).isEmpty();

        // 2. Build Hierarchy Index
        TypeHierarchyIndex hierarchyIndex = new TypeHierarchyIndex(symbolTable);
        symbolTable.allTypes().forEach(hierarchyIndex::index);

        // 3. Pass 2 Invocations Resolution
        Pass2InvocationResolver pass2Resolver = new Pass2InvocationResolver(
                symbolTable,
                hierarchyIndex,
                ResolutionChain.standardChain()
        );

        List<ParsedCompilationUnit> units = new ArrayList<>();
        try (var stream = Files.walk(tempDir)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path f : javaFiles) {
                String source = Files.readString(f);
                units.add(pass2Resolver.resolve(f, source));
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        long peakMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long memoryDeltaMb = (peakMemory - initialMemory) / (1024 * 1024);

        System.out.printf("Stress Test Completed: %d classes parsed and resolved in %d ms (Pass 1: %d ms)%n",
                totalClasses, totalDuration, pass1Duration);
        System.out.printf("Peak Heap Memory: %d MB (Delta: %d MB)%n", peakMemory / (1024 * 1024), memoryDeltaMb);

        // Assertions
        assertThat(units).hasSize(totalClasses);
        assertThat(totalDuration).isLessThan(15000); // Must be under 15 seconds
        assertThat(peakMemory).isLessThan(512L * 1024 * 1024); // Must be under 512 MB ceiling

        // Check resolution ratio
        double totalRatio = units.stream().mapToDouble(u -> u.metrics().resolutionRatio()).average().orElse(0.0);
        assertThat(totalRatio).isGreaterThanOrEqualTo(0.99);
    }
}