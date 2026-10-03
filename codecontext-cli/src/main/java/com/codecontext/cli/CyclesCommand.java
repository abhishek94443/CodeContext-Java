package com.codecontext.cli;

import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.analytics.CycleDetectionEngine;
import com.codecontext.core.graph.analytics.CycleOptions;
import com.codecontext.core.graph.analytics.CyclePath;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.model.DependencyEdge;
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
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "cycles",
        description = "Detects circular dependency loops using Tarjan SCC algorithm"
)
public class CyclesCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"--fail-on-cycles"}, description = "Fail with exit code 1 if circular dependencies are detected")
    private boolean failOnCycles;

    @Option(names = {"--json"}, description = "Output cycles as JSON")
    private boolean json;

    @Option(names = {"--include-tests"}, description = "Include test classes in circular dependency analysis")
    private boolean includeTests;

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
        scanner.scan(repoPath, symbolTable, includeTests);

        TypeHierarchyIndex hierarchyIndex = new TypeHierarchyIndex(symbolTable);
        symbolTable.allTypes().forEach(hierarchyIndex::index);

        Pass2InvocationResolver resolver = new Pass2InvocationResolver(
                symbolTable,
                hierarchyIndex,
                ResolutionChain.standardChain()
        );

        List<Path> javaFiles = SourceFileFinder.find(repoPath, includeTests);
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

        // 2. Build graph & detect cycles
        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(units, GraphBuildOptions.defaults());

        CycleDetectionEngine cycleEngine = new CycleDetectionEngine();
        CycleReport cycleReport = cycleEngine.detectCycles(graph, CycleOptions.defaults());

        int cycleCount = cycleReport.cycles().size();

        if (json) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("totalCycles", cycleCount);
            root.put("isTruncated", cycleReport.isTruncated());

            ArrayNode cyclesNode = root.putArray("cycles");
            for (CyclePath cycle : cycleReport.cycles()) {
                ObjectNode cNode = cyclesNode.addObject();
                cNode.put("length", cycle.length());
                cNode.put("cyclePath", formatCycle(cycle));
                ArrayNode classesNode = cNode.putArray("classes");
                cycle.fqcns().forEach(classesNode::add);
            }

            out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } else {
            if (cycleCount == 0) {
                out.println("Clean architecture: 0 circular dependencies detected.");
            } else {
                out.printf("Detected %d circular dependency loop%s:%n", cycleCount, cycleCount > 1 ? "s" : "");
                for (int i = 0; i < cycleReport.cycles().size(); i++) {
                    CyclePath cycle = cycleReport.cycles().get(i);
                    out.printf(" [%d] %s%n", i + 1, formatCycle(cycle));
                }
            }
        }

        if (failOnCycles && cycleCount > 0) {
            err.printf("CI Gate Violation: %d circular dependency loops detected!%n", cycleCount);
            return CliExitCode.VIOLATION.getCode();
        }

        return CliExitCode.SUCCESS.getCode();
    }

    public String formatCycle(CyclePath cycle) {
        return String.join(" -> ", cycle.fqcns());
    }
}
