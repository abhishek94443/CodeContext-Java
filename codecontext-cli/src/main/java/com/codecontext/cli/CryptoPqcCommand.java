package com.codecontext.cli;

import com.codecontext.cli.terminal.TerminalCapabilityDetector;
import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.analytics.PageRankEngine;
import com.codecontext.core.graph.analytics.PageRankOptions;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.model.ParsedCompilationUnit;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.parser.BoundedPass1Scanner;
import com.codecontext.core.parser.Pass2InvocationResolver;
import com.codecontext.core.parser.SourceFileFinder;
import com.codecontext.core.resolver.ResolutionChain;
import com.codecontext.core.security.SecurityAnalysisEngine;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.inventory.CryptoAssetUsage;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoInventoryEngine;
import com.codecontext.core.security.pqc.CryptoAssetBlastRadius;
import com.codecontext.core.security.pqc.PqcAssetAssessment;
import com.codecontext.core.security.pqc.PqcAssessmentReport;
import com.codecontext.core.security.pqc.PqcMigrationEngine;
import com.codecontext.core.security.rule.SecurityRuleRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI subcommand: codecontext crypto pqc [PATH] [OPTIONS]
 * Assesses cryptographic assets for Post-Quantum Cryptography (PQC) migration exposure and architectural blast radius.
 */
@Command(
        name = "pqc",
        description = "Evaluates Post-Quantum Cryptography (PQC) exposure, NIST migration targets, and call-graph blast radius",
        mixinStandardHelpOptions = true
)
public class CryptoPqcCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"-o", "--output"}, description = "Target output file path")
    private Path outputFile;

    @Option(names = {"--format"}, description = "Output format (table, json)", defaultValue = "table")
    private String format;

    @Option(names = {"--max-depth"}, description = "Maximum call-graph traversal depth for blast radius", defaultValue = "10")
    private int maxDepth;

    @Option(names = {"--include-tests"}, description = "Include test source files in analysis")
    private boolean includeTests;

    @Option(names = {"--no-color"}, description = "Disable ANSI color escapes")
    private boolean noColor;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        Path resolvedPath = repoPath == null ? Path.of(".").toAbsolutePath().normalize() : repoPath.toAbsolutePath().normalize();
        if (!Files.exists(resolvedPath)) {
            err.printf("Repository path does not exist: %s%n", resolvedPath);
            return CliExitCode.BAD_ARGS.getCode();
        }

        // 1. Scan for Cryptographic Usages
        SecurityAnalysisEngine analysisEngine = new SecurityAnalysisEngine(SecurityRuleRegistry.empty());
        SecurityAnalysisResult analysisResult = analysisEngine.analyze(resolvedPath, null, includeTests);

        CryptoInventoryEngine inventoryEngine = new CryptoInventoryEngine();
        CryptoInventory inventory = inventoryEngine.buildInventory(analysisResult.cryptoUsages(), resolvedPath);

        // 2. Build Dependency Graph for Blast Radius Traversal
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = null;
        Map<String, HotspotMetric> metrics = null;
        try {
            ConcurrentSymbolTable symbolTable = new ConcurrentSymbolTable();
            BoundedPass1Scanner scanner = new BoundedPass1Scanner();
            scanner.scan(resolvedPath, symbolTable, includeTests);

            TypeHierarchyIndex hierarchyIndex = new TypeHierarchyIndex(symbolTable);
            symbolTable.allTypes().forEach(hierarchyIndex::index);

            Pass2InvocationResolver resolver = new Pass2InvocationResolver(
                    symbolTable,
                    hierarchyIndex,
                    ResolutionChain.standardChain()
            );

            List<Path> javaFiles = SourceFileFinder.find(resolvedPath, includeTests);
            List<ParsedCompilationUnit> units = new ArrayList<>(javaFiles.size());
            for (Path f : javaFiles) {
                String source = Files.readString(f);
                ParsedCompilationUnit rawUnit = resolver.resolve(f, source);
                List<TypeDefinition> typesInFile = symbolTable.allTypes().stream()
                        .filter(t -> t.sourceFilePath().equals(f))
                        .toList();

                units.add(new ParsedCompilationUnit(
                        f,
                        rawUnit.packageName(),
                        rawUnit.imports(),
                        typesInFile,
                        rawUnit.invocations(),
                        rawUnit.metrics(),
                        rawUnit.parseErrors()
                ));
            }

            DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder();
            graph = graphBuilder.build(units, GraphBuildOptions.defaults());

            PageRankEngine prEngine = new PageRankEngine();
            List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());
            metrics = hotspots.stream().collect(Collectors.toMap(HotspotMetric::fqcn, h -> h, (a, b) -> a));
        } catch (Exception ignored) {
            // Graceful fallback to null graph when repository cannot be parsed as a full Java project
        }

        // 3. Execute PQC Migration Engine
        PqcMigrationEngine pqcEngine = new PqcMigrationEngine();
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, metrics, maxDepth);

        String resolvedFormat = format.toLowerCase(Locale.ROOT).trim();
        if (outputFile != null && outputFile.toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            resolvedFormat = "json";
        }

        if ("json".equals(resolvedFormat)) {
            String jsonOutput = serializeJsonReport(report, analysisResult.filesScanned(), resolvedPath);
            if (outputFile != null) {
                Path target = outputFile.isAbsolute() ? outputFile : resolvedPath.resolve(outputFile);
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, jsonOutput, StandardCharsets.UTF_8);
                out.printf("PQC Migration Assessment JSON written to: %s (%d assets)%n",
                        target.toAbsolutePath(), report.totalAssetsAnalyzed());
            } else {
                out.println(jsonOutput);
            }
        } else {
            // High-contrast Table Output
            boolean ansi = TerminalCapabilityDetector.isAnsiSupported(noColor);
            String tableOutput = renderTableReport(report, resolvedPath, analysisResult.filesScanned(), ansi);

            if (outputFile != null) {
                Path target = outputFile.isAbsolute() ? outputFile : resolvedPath.resolve(outputFile);
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                // When saving to file, strip ANSI codes
                String plainOutput = renderTableReport(report, resolvedPath, analysisResult.filesScanned(), false);
                Files.writeString(target, plainOutput, StandardCharsets.UTF_8);
                out.printf("PQC Migration Assessment report written to: %s (%d assets)%n",
                        target.toAbsolutePath(), report.totalAssetsAnalyzed());
            } else {
                out.print(tableOutput);
                out.flush();
            }
        }

        return CliExitCode.SUCCESS.getCode();
    }

    private String serializeJsonReport(PqcAssessmentReport report, int filesScanned, Path repoRoot) throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode root = mapper.createObjectNode();

        ObjectNode summaryNode = root.putObject("summary");
        summaryNode.put("filesScanned", filesScanned);
        summaryNode.put("totalAssets", report.totalAssetsAnalyzed());
        summaryNode.put("quantumVulnerable", report.quantumVulnerableCount());
        summaryNode.put("quantumImpacted", report.quantumImpactedCount());
        summaryNode.put("classicallyWeak", report.classicallyWeakCount());
        summaryNode.put("quantumSafe", report.quantumSafeCount());
        summaryNode.put("unknown", report.unknownExposureCount());
        summaryNode.put("pqcMigrationCandidates", report.pqcMigrationCandidatesCount());
        summaryNode.put("classicalRemediation", report.classicalRemediationCount());
        summaryNode.put("quantumConsiderations", report.quantumConsiderationsCount());
        summaryNode.put("unknownOrUnresolved", report.unknownOrUnresolvedCount());
        summaryNode.put("criticalPriority", report.criticalPriorityCount());
        summaryNode.put("highPriority", report.highPriorityCount());
        summaryNode.put("mediumPriority", report.mediumPriorityCount());
        summaryNode.put("lowPriority", report.lowPriorityCount());
        summaryNode.put("reviewRequiredPriority", report.reviewRequiredPriorityCount());
        summaryNode.put("undeterminedPriority", report.undeterminedPriorityCount());
        summaryNode.put("totalCallerClassesAffected", report.totalAffectedClassesInBlastRadius());

        ObjectNode expMap = summaryNode.putObject("exposureBreakdown");
        report.exposureBreakdown().forEach((k, v) -> expMap.put(k.name(), v));

        ObjectNode purpMap = summaryNode.putObject("purposeBreakdown");
        report.purposeBreakdown().forEach((k, v) -> purpMap.put(k.name(), v));

        ArrayNode assessmentsArr = root.putArray("assessments");
        for (PqcAssetAssessment a : report.assessments()) {
            ObjectNode an = assessmentsArr.addObject();
            an.put("assetId", a.asset().assetId());
            an.put("name", a.asset().name());
            an.put("algorithmFamily", a.asset().algorithmFamily());
            an.put("primitiveType", a.asset().primitiveType().name());
            an.put("category", a.category().name());
            an.put("categoryDisplayName", a.category().displayName());
            an.put("exposureLevel", a.exposureLevel().name());
            an.put("purpose", a.purpose().name());
            an.put("priority", a.priority().name());
            an.put("threatSummary", a.quantumThreatSummary());

            ObjectNode recNode = an.putObject("recommendation");
            recNode.put("targetStandard", a.recommendation().targetStandard());
            recNode.put("standardFamily", a.recommendation().standardFamily());
            recNode.put("transitionStrategy", a.recommendation().transitionStrategy());
            recNode.put("nistGuidanceRationale", a.recommendation().rationale());
            recNode.put("fipsReference", a.recommendation().standardFamily());

            ObjectNode brNode = an.putObject("blastRadius");
            ArrayNode directArr = brNode.putArray("directDependentClasses");
            a.blastRadius().directDependentClasses().forEach(directArr::add);

            ArrayNode transOnlyArr = brNode.putArray("transitiveOnlyDependentClasses");
            a.blastRadius().transitiveOnlyDependentClasses().forEach(transOnlyArr::add);

            brNode.put("totalDirectDependents", a.blastRadius().totalDirectDependents());
            brNode.put("totalTransitiveOnlyDependents", a.blastRadius().totalTransitiveOnlyDependents());
            brNode.put("totalReachableClasses", a.blastRadius().totalReachableClasses());
            brNode.put("maxCallDepth", a.blastRadius().maxCallDepth());
            brNode.put("cumulativeRiskScore", a.blastRadius().cumulativeRiskScore());
            brNode.put("isSharedUtilityUsage", a.blastRadius().isSharedUtilityUsage());
            brNode.put("reachabilitySemanticsNote", a.blastRadius().reachabilitySemanticsNote());

            ArrayNode cusArr = brNode.putArray("affectedCompilationUnits");
            a.blastRadius().affectedCompilationUnits().forEach(cusArr::add);

            ArrayNode usagesArr = an.putArray("usages");
            for (CryptoAssetUsage u : a.asset().usages()) {
                ObjectNode un = usagesArr.addObject();
                un.put("filePath", relativizePath(u.filePath(), repoRoot));
                un.put("line", u.location().startLine());
                un.put("column", u.location().startColumn());
                un.put("apiOwner", u.apiOwner().name());
                un.put("methodName", u.methodName());
                un.put("originalArgument", u.originalArgument());
                un.put("status", u.argumentResolution().status().name());
            }
        }

        return mapper.writeValueAsString(root);
    }

    private String renderTableReport(PqcAssessmentReport report, Path repoRoot, int filesScanned, boolean ansi) {
        StringBuilder sb = new StringBuilder();
        String bold = ansi ? "\u001B[1m" : "";
        String reset = ansi ? "\u001B[0m" : "";
        String red = ansi ? "\u001B[31m" : "";
        String yellow = ansi ? "\u001B[33m" : "";
        String green = ansi ? "\u001B[32m" : "";
        String cyan = ansi ? "\u001B[36m" : "";
        String magenta = ansi ? "\u001B[35m" : "";

        sb.append("====================================================================================================\n");
        sb.append(String.format(" %sCodeContext Post-Quantum Cryptography (PQC) Migration Assessment%s%n", bold, reset));
        sb.append("====================================================================================================\n");
        sb.append(String.format(" Repository Path             : %s%n", repoRoot));
        sb.append(String.format(" Files Scanned               : %d%n", filesScanned));
        sb.append(String.format(" Total Cryptographic Assets  : %d%n", report.totalAssetsAnalyzed()));
        sb.append(String.format(" PQC Migration Candidates    : %s%d%s (Public-key assets broken by Shor's algorithm)%n",
                red, report.pqcMigrationCandidatesCount(), reset));
        sb.append(String.format(" Classical Remediation       : %s%d%s (Deprecated classical algorithms requiring fix)%n",
                magenta, report.classicalRemediationCount(), reset));
        sb.append(String.format(" Quantum Impacted (Grover)   : %s%d%s (Modern symmetric/hash considerations: retain baseline)%n",
                yellow, report.quantumImpactedCount(), reset));
        sb.append(String.format(" Unknown / Unresolved        : %s%d%s (Dynamic expressions or provider-dependent usages)%n",
                cyan, report.unknownOrUnresolvedCount(), reset));
        sb.append(String.format(" Review Required Priority    : %s%d%s (Requires organizational business review)%n",
                cyan, report.reviewRequiredPriorityCount(), reset));
        sb.append(String.format(" Caller Classes Affected     : %s%d%s across dependency graph%n",
                cyan, report.totalAffectedClassesInBlastRadius(), reset));
        sb.append("====================================================================================================\n");
        sb.append(String.format(" %-28s %-18s %-22s %-16s %-16s %s%n",
                "Asset / Algorithm", "Exposure Level", "Purpose", "Priority", "NIST Target", "Blast Radius"));
        sb.append("----------------------------------------------------------------------------------------------------\n");

        if (report.assessments().isEmpty()) {
            sb.append(" No cryptographic assets detected in repository.\n");
        } else {
            for (PqcAssetAssessment a : report.assessments()) {
                String expColor = switch (a.exposureLevel()) {
                    case QUANTUM_VULNERABLE -> red;
                    case QUANTUM_IMPACTED -> yellow;
                    case CLASSICALLY_WEAK -> magenta;
                    case QUANTUM_SAFE -> green;
                    case UNKNOWN -> "";
                };

                String prioColor = switch (a.priority()) {
                    case CRITICAL, HIGH -> red;
                    case MEDIUM, REVIEW_REQUIRED -> yellow;
                    case LOW, INFO, PRIORITY_UNDETERMINED -> "";
                };

                CryptoAssetBlastRadius br = a.blastRadius();
                String utilMarker = br.isSharedUtilityUsage() ? "*" : "";
                String brSummary = String.format("%d dir / %d trans-only (depth %d)%s",
                        br.totalDirectDependents(),
                        br.totalTransitiveOnlyDependents(),
                        br.maxCallDepth(),
                        utilMarker);

                sb.append(String.format(" %-28s %s%-18s%s %-22s %s%-16s%s %-16s %s%n",
                        truncate(a.asset().name(), 28),
                        expColor, truncate(a.exposureLevel().name(), 18), reset,
                        truncate(a.purpose().name(), 22),
                        prioColor, truncate(a.priority().name(), 16), reset,
                        truncate(a.recommendation().targetStandard(), 16),
                        brSummary
                ));
            }
        }
        sb.append("====================================================================================================\n");
        return sb.toString();
    }

    private static String relativizePath(Path path, Path repoRoot) {
        if (path == null) return "unknown";
        if (repoRoot == null) return path.toString().replace('\\', '/');
        try {
            Path normRepo = repoRoot.toAbsolutePath().normalize();
            Path normPath = path.toAbsolutePath().normalize();
            if (normPath.startsWith(normRepo)) {
                return normRepo.relativize(normPath).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return path.toString().replace('\\', '/');
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }
}
