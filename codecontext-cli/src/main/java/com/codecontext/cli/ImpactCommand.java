package com.codecontext.cli;

import com.codecontext.cli.terminal.TerminalCapabilityDetector;
import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.analytics.*;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.EdgeReversedGraph;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "impact",
        description = "Computes the direct and transitive blast radius of modifying a target class"
)
public class ImpactCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target class name (simple name or FQCN)")
    private String targetClass;

    @Option(names = {"--path"}, description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"-d", "--depth"}, description = "Maximum traversal depth for transitive callers", defaultValue = "3")
    private int depth;

    @Option(names = {"--suggest-tests"}, description = "Suggest test suites to run for impacted classes")
    private boolean suggestTests;

    @Option(names = {"--json"}, description = "Output impact hierarchy as JSON")
    private boolean json;

    @Option(names = {"--no-color"}, description = "Disable ANSI color escapes")
    private boolean noColor;

    @picocli.CommandLine.Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository path does not exist: " + repoPath);
        }

        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        // 1. Scan & Resolve
        ConcurrentSymbolTable symbolTable = new ConcurrentSymbolTable();
        BoundedPass1Scanner scanner = new BoundedPass1Scanner();
        scanner.scan(repoPath, symbolTable, true);

        TypeHierarchyIndex hierarchyIndex = new TypeHierarchyIndex(symbolTable);
        symbolTable.allTypes().forEach(hierarchyIndex::index);

        Pass2InvocationResolver resolver = new Pass2InvocationResolver(
                symbolTable,
                hierarchyIndex,
                ResolutionChain.standardChain()
        );

        List<Path> javaFiles = SourceFileFinder.find(repoPath, true);
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

        // 2. Build graph & metrics
        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(units, GraphBuildOptions.defaults());

        PageRankEngine prEngine = new PageRankEngine();
        List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());
        Map<String, HotspotMetric> hotspotMap = new HashMap<>();
        for (HotspotMetric h : hotspots) {
            hotspotMap.put(h.fqcn(), h);
        }

        // 3. Resolve Target Vertex
        TypeVertex target = graph.vertexSet().stream()
                .filter(v -> v.fqcn().equals(targetClass))
                .findFirst()
                .orElse(null);

        if (target == null) {
            target = graph.vertexSet().stream()
                    .filter(v -> v.simpleName().equals(targetClass))
                    .findFirst()
                    .orElse(null);
        }

        if (target == null) {
            err.printf("Class not found in symbol index: %s%n", targetClass);
            return CliExitCode.BAD_ARGS.getCode();
        }

        // 4. Run BlastRadiusEngine
        BlastRadiusEngine engine = new BlastRadiusEngine();
        ImpactAnalysisResult impact = engine.analyzeImpact(target.fqcn(), graph, hotspotMap, depth);

        // 5. If --suggest-tests, detect test classes
        List<String> suggestedTests = new ArrayList<>(impact.recommendedTestScope());
        if (suggestTests) {
            Set<String> impactedSimpleNames = new HashSet<>();
            impactedSimpleNames.add(target.simpleName());
            for (String callerFqcn : impact.transitiveCallerDepths().keySet()) {
                int lastDot = callerFqcn.lastIndexOf('.');
                impactedSimpleNames.add(lastDot > 0 ? callerFqcn.substring(lastDot + 1) : callerFqcn);
            }

            for (Path p : javaFiles) {
                String fileName = p.getFileName().toString();
                if (fileName.endsWith("Test.java") || fileName.endsWith("Tests.java") || fileName.endsWith("TestCase.java")) {
                    String baseName = fileName.replace(".java", "");
                    for (String impName : impactedSimpleNames) {
                        if (baseName.contains(impName)) {
                            if (!suggestedTests.contains(baseName)) {
                                suggestedTests.add(baseName);
                            }
                        }
                    }
                }
            }
        }

        // 6. Output Formatting
        if (json) {
            renderJson(out, target, impact, suggestedTests);
        } else {
            renderTerminal(out, target, impact, graph, suggestedTests);
        }

        return CliExitCode.SUCCESS.getCode();
    }

    private void renderJson(PrintWriter out, TypeVertex target, ImpactAnalysisResult impact, List<String> tests) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("targetClass", target.simpleName());
        root.put("targetFqcn", target.fqcn());
        root.put("inDegree", impact.directCallers().size());
        root.put("totalBlastRadius", impact.transitiveCallerDepths().size());
        root.put("cumulativeRisk", Math.round(impact.cumulativeRisk() * 1000.0) / 1000.0);

        ArrayNode callersNode = root.putArray("directCallers");
        impact.directCallers().forEach(callersNode::add);

        ObjectNode transitiveNode = root.putObject("transitiveCallers");
        impact.transitiveCallerDepths().forEach(transitiveNode::put);

        ArrayNode testsNode = root.putArray("recommendedTestScope");
        tests.forEach(testsNode::add);

        out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private void renderTerminal(PrintWriter out, TypeVertex target, ImpactAnalysisResult impact,
                                DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
                                List<String> tests) {
        out.println("================================================================================");
        out.printf(" Blast Radius Analysis: %s (%s)%n", target.simpleName(), target.fqcn());
        out.println("================================================================================");
        out.printf(" Direct Callers (In-degree: %d):%n", impact.directCallers().size());
        if (impact.directCallers().isEmpty()) {
            out.println("   (No direct internal callers detected - component is a root entry point)");
        } else {
            for (String caller : impact.directCallers()) {
                out.printf("   +-- %s%n", caller);
            }
        }
        out.println();

        out.printf(" Transitive Upstream Tree (Depth: %d):%n", depth);
        out.printf("   [%s]%n", target.simpleName());

        EdgeReversedGraph<TypeVertex, DependencyEdge> rev = new EdgeReversedGraph<>(graph);
        renderTreeRecursive(out, target, rev, 1, depth, new HashSet<>());

        out.println("--------------------------------------------------------------------------------");
        out.printf(" Total Blast Radius: %d impacted classes%n", impact.transitiveCallerDepths().size());
        out.printf(" Cumulative Risk Score: %.4f%n", impact.cumulativeRisk());

        if (suggestTests) {
            out.println("--------------------------------------------------------------------------------");
            out.println(" Recommended Test Scope:");
            if (tests.isEmpty()) {
                out.println("   (No dedicated test suites detected for impacted classes)");
            } else {
                for (String t : tests) {
                    out.printf("   - %s%n", t);
                }
            }
        }
        out.println("================================================================================");
    }

    private void renderTreeRecursive(PrintWriter out, TypeVertex current,
                                    EdgeReversedGraph<TypeVertex, DependencyEdge> rev,
                                    int currentDepth, int maxDepth, Set<TypeVertex> visited) {
        if (currentDepth > maxDepth || visited.contains(current)) {
            return;
        }
        visited.add(current);

        String indent = "  " + "      ".repeat(currentDepth);
        for (DependencyEdge edge : rev.outgoingEdgesOf(current)) {
            TypeVertex caller = rev.getEdgeTarget(edge);
            out.printf("%s<-- [%s] (depth %d)%n", indent, caller.simpleName(), currentDepth);
            renderTreeRecursive(out, caller, rev, currentDepth + 1, maxDepth, new HashSet<>(visited));
        }
    }
}
