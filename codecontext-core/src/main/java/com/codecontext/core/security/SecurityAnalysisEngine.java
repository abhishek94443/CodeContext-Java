package com.codecontext.core.security;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.index.SymbolLookup;
import com.codecontext.core.parser.SourceFileFinder;
import com.codecontext.core.security.crypto.CryptoApiDetector;
import com.codecontext.core.security.crypto.CryptoApiUsage;
import com.codecontext.core.security.rule.CompilationUnitContext;
import com.codecontext.core.security.rule.SecurityRule;
import com.codecontext.core.security.rule.SecurityRuleRegistry;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * High-performance, virtual-thread powered engine executing security and cryptographic AST rules.
 * Enforces bounded concurrency, graceful parse failure recovery, and zero global AST retention.
 * Discovers cryptographic API usages and performs explicit finding deduplication.
 */
public class SecurityAnalysisEngine {

    private static final ParserConfiguration CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    private final SecurityRuleRegistry ruleRegistry;
    private final Semaphore semaphore;

    public SecurityAnalysisEngine() {
        this(SecurityRuleRegistry.defaultRegistry());
    }

    public SecurityAnalysisEngine(SecurityRuleRegistry ruleRegistry) {
        int concurrentPermits = Math.max(4, Runtime.getRuntime().availableProcessors() * 4);
        this.ruleRegistry = Objects.requireNonNull(ruleRegistry, "ruleRegistry cannot be null");
        this.semaphore = new Semaphore(concurrentPermits);
    }

    public SecurityAnalysisEngine(SecurityRuleRegistry ruleRegistry, int maxConcurrentPermits) {
        this.ruleRegistry = Objects.requireNonNull(ruleRegistry, "ruleRegistry cannot be null");
        this.semaphore = new Semaphore(Math.max(1, maxConcurrentPermits));
    }

    public SecurityRuleRegistry ruleRegistry() {
        return ruleRegistry;
    }

    /**
     * Executes security analysis over all Java source files under the given repository directory.
     *
     * @param repoPath     Root path of repository to analyze.
     * @param symbolLookup Optional symbol table for cross-class resolution.
     * @param includeTests Whether to include test source files.
     * @return Deterministically ordered and deduplicated analysis result.
     */
    public SecurityAnalysisResult analyze(Path repoPath, SymbolLookup symbolLookup, boolean includeTests) {
        Objects.requireNonNull(repoPath, "repoPath cannot be null");

        List<RuleMetadata> activeRules = ruleRegistry.allRules().stream()
                .map(SecurityRule::metadata)
                .toList();

        List<Path> files = SourceFileFinder.find(repoPath, includeTests);
        long startTime = System.currentTimeMillis();

        ConcurrentLinkedQueue<Finding> findingsQueue = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<CryptoApiUsage> usagesQueue = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Path> failedFiles = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> errorsQueue = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>(files.size());

            for (Path file : files) {
                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        String code = Files.readString(file);
                        JavaParser parser = new JavaParser(CONFIG);
                        ParseResult<CompilationUnit> parseResult = parser.parse(code);

                        if (!parseResult.isSuccessful()) {
                            failedFiles.add(file);
                            parseResult.getProblems().forEach(p ->
                                errorsQueue.add("Parse error in " + file + " [line " +
                                    (p.getLocation().isPresent() ? p.getLocation().get().getBegin().getRange().map(r -> r.begin.line).orElse(0) : 0) +
                                    "]: " + p.getMessage())
                            );
                        }

                        if (parseResult.getResult().isPresent()) {
                            CompilationUnit cu = parseResult.getResult().get();
                            CompilationUnitContext context = new CompilationUnitContext(
                                    file,
                                    code,
                                    cu,
                                    Optional.ofNullable(symbolLookup)
                            );

                            // 1. Discover cryptographic API usages (for inventory and observation)
                            List<CryptoApiUsage> fileUsages = CryptoApiDetector.detectAll(context);
                            usagesQueue.addAll(fileUsages);

                            // 2. Evaluate active security rules
                            List<Finding> fileFindings = evaluateRules(context, errorsQueue);
                            findingsQueue.addAll(fileFindings);
                        }
                    } catch (Exception e) {
                        failedFiles.add(file);
                        errorsQueue.add("Failed to process file " + file + ": " + e.getMessage());
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
                    errorsQueue.add("Task execution exception: " + e.getMessage());
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Explicit deduplication using stable identity, distinct from sorting
        List<Finding> deduplicatedFindings = Finding.deduplicate(findingsQueue);

        List<CryptoApiUsage> sortedUsages = new ArrayList<>(usagesQueue);
        sortedUsages.sort((a, b) -> {
            int p = a.filePath().compareTo(b.filePath());
            if (p != 0) return p;
            return Integer.compare(a.location().startLine(), b.location().startLine());
        });

        return new SecurityAnalysisResult(
                deduplicatedFindings,
                files.size(),
                new ArrayList<>(failedFiles),
                new ArrayList<>(errorsQueue),
                duration,
                activeRules,
                sortedUsages
        );
    }

    /**
     * Analyzes an existing parsed CompilationUnit directly, avoiding duplicate parsing.
     */
    public List<Finding> analyzeCompilationUnit(
            Path filePath,
            String sourceCode,
            CompilationUnit cu,
            Optional<SymbolLookup> symbolLookup
    ) {
        return analyzeCompilationUnit(filePath, sourceCode, cu, symbolLookup, null);
    }

    public List<Finding> analyzeCompilationUnit(
            Path filePath,
            String sourceCode,
            CompilationUnit cu,
            Optional<SymbolLookup> symbolLookup,
            ConcurrentLinkedQueue<String> errorsSink
    ) {
        if (cu == null) {
            return List.of();
        }

        CompilationUnitContext context = new CompilationUnitContext(filePath, sourceCode, cu, symbolLookup);
        List<Finding> rawFindings = evaluateRules(context, errorsSink);
        return Finding.deduplicate(rawFindings);
    }

    private List<Finding> evaluateRules(CompilationUnitContext context, ConcurrentLinkedQueue<String> errorsSink) {
        if (ruleRegistry.isEmpty()) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        for (SecurityRule rule : ruleRegistry.allRules()) {
            try {
                List<Finding> ruleFindings = rule.analyze(context);
                if (ruleFindings != null && !ruleFindings.isEmpty()) {
                    findings.addAll(ruleFindings);
                }
            } catch (Exception e) {
                if (errorsSink != null) {
                    errorsSink.add("Rule [" + rule.id() + "] error on " + context.filePath() + ": " + e.getMessage());
                }
            }
        }
        return findings;
    }
}
