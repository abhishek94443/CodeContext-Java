package com.codecontext.core.parser;

import com.codecontext.core.index.SymbolRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded parallel scanner executing Pass 1 declaration cataloging.
 * Uses Java 21 Virtual Threads bounded by a Semaphore to guarantee predictable memory footprint.
 */
public class BoundedPass1Scanner {

    private final Semaphore semaphore;
    private final JavaParserDeclarationVisitor visitor;

    public BoundedPass1Scanner() {
        int permits = Math.max(4, Runtime.getRuntime().availableProcessors() * 4);
        this.semaphore = new Semaphore(permits);
        this.visitor = new JavaParserDeclarationVisitor();
    }

    public BoundedPass1Scanner(int maxConcurrentPermits) {
        this.semaphore = new Semaphore(maxConcurrentPermits);
        this.visitor = new JavaParserDeclarationVisitor();
    }

    /**
     * Discovers and parses all .java files under the root directory into the provided SymbolRegistry.
     */
    public ScanSummary scan(Path rootDir, SymbolRegistry registry) {
        return scan(rootDir, registry, false);
    }

    public ScanSummary scan(Path rootDir, SymbolRegistry registry, boolean includeTests) {
        List<Path> files = SourceFileFinder.find(rootDir, includeTests);
        AtomicInteger typesCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Path> failedFiles = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();

            for (Path file : files) {
                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        String code = Files.readString(file);
                        ParseResult result = visitor.parseAndRegister(code, file, registry);
                        typesCount.addAndGet(result.typesDiscovered().size());
                        if (result.hasErrors() && result.typesDiscovered().isEmpty()) {
                            failedFiles.add(file);
                        }
                    } catch (Exception e) {
                        failedFiles.add(file);
                    } finally {
                        semaphore.release();
                    }
                    return null;
                }));
            }

            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    // Task failure already captured in failedFiles queue
                }
            }
        }

        return new ScanSummary(files.size(), typesCount.get(), new ArrayList<>(failedFiles));
    }
}