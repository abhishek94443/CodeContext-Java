package com.codecontext.cli;

import com.codecontext.core.git.GitEvolutionMetric;
import com.codecontext.core.git.GitHistoryProvider;
import com.codecontext.core.git.GitHistoryProviderFactory;
import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.analytics.CycleDetectionEngine;
import com.codecontext.core.graph.analytics.CycleOptions;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.analytics.PageRankEngine;
import com.codecontext.core.graph.analytics.PageRankOptions;
import com.codecontext.core.graph.export.HtmlReportExporter;
import com.codecontext.core.graph.export.HtmlReportPayload;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.graph.risk.CompositeRiskEngine;
import com.codecontext.core.graph.risk.CompositeRiskScore;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "analyze",
        description = "Performs full repository analysis, PageRank hotspots, and HTML blueprint generation"
)
public class AnalyzeCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"--output"}, description = "Directory to write output HTML report")
    private Path outputDir;

    @Option(names = {"--json"}, description = "Output JSON format")
    private boolean json;

    @Option(names = {"-q", "--quiet"}, description = "Quiet mode")
    private boolean quiet;

    @Option(names = {"--no-color"}, description = "Disable ANSI color escapes")
    private boolean noColor;

    @Option(names = {"--include-tests"}, description = "Include test classes in architecture analysis")
    private boolean includeTests;

    @picocli.CommandLine.Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository path does not exist: " + repoPath);
        }

        PrintWriter out = spec.commandLine().getOut();

        // 1. Pass 1 Scan
        ConcurrentSymbolTable symbolTable = new ConcurrentSymbolTable();
        BoundedPass1Scanner scanner = new BoundedPass1Scanner();
        scanner.scan(repoPath, symbolTable, includeTests);

        // 2. Type Hierarchy
        TypeHierarchyIndex hierarchyIndex = new TypeHierarchyIndex(symbolTable);
        symbolTable.allTypes().forEach(hierarchyIndex::index);

        // 3. Pass 2 Resolution
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

        // 4. Graph Construction
        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(units, GraphBuildOptions.defaults());

        // 5. PageRank & Cycles
        PageRankEngine prEngine = new PageRankEngine();
        List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());

        CycleDetectionEngine cycleEngine = new CycleDetectionEngine();
        CycleReport cycleReport = cycleEngine.detectCycles(graph, CycleOptions.defaults());

        // 6. Git Metrics & Composite Risk
        GitHistoryProvider gitProvider = GitHistoryProviderFactory.createProvider(repoPath);
        boolean gitAvailable = gitProvider.isAvailable();
        Map<String, GitEvolutionMetric> gitMetrics = gitAvailable ? gitProvider.extractMetrics(repoPath, 90) : Map.of();

        CompositeRiskEngine riskEngine = new CompositeRiskEngine();
        List<CompositeRiskScore> riskScores = riskEngine.calculate(hotspots, cycleReport, gitMetrics, gitAvailable);

        // 7. HTML Blueprint Export (CR-S3.1-02 & FEAT-S3.1-01)
        Path targetHtml = outputDir != null
                ? outputDir.resolve("codecontext-graph.html")
                : repoPath.resolve("build/reports/codecontext-graph.html");

        Map<String, TypeDefinition> typeDefs = new HashMap<>();
        for (TypeDefinition t : symbolTable.allTypes()) {
            typeDefs.put(t.fqcn(), t);
        }

        HtmlReportExporter exporter = new HtmlReportExporter();
        exporter.export(new HtmlReportPayload(graph, hotspots, cycleReport, riskScores, typeDefs, gitMetrics, hierarchyIndex), targetHtml);

        if (json) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("totalFiles", units.size());
            root.put("totalClasses", graph.vertexSet().size());
            root.put("totalInvocations", graph.edgeSet().size());
            root.put("totalCycles", cycleReport.cycles().size());
            root.put("reportPath", targetHtml.toAbsolutePath().toString());

            ArrayNode hotspotsNode = root.putArray("hotspots");
            for (int i = 0; i < Math.min(10, hotspots.size()); i++) {
                HotspotMetric m = hotspots.get(i);
                ObjectNode hNode = hotspotsNode.addObject();
                hNode.put("rank", m.rank());
                hNode.put("fqcn", m.fqcn());
                hNode.put("pageRank", m.rawScore());
                hNode.put("inDegree", m.inDegree());
                hNode.put("outDegree", m.outDegree());
            }

            out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } else if (quiet) {
            out.println(targetHtml.toAbsolutePath());
        } else {
            renderDashboard(out, graph.vertexSet().size(), graph.edgeSet().size(), cycleReport.cycles().size(), hotspots, targetHtml);
        }

        return CliExitCode.SUCCESS.getCode();
    }

    private void renderDashboard(PrintWriter out, int classes, int edges, int cycles, List<HotspotMetric> hotspots, Path reportPath) {
        out.println("================================================================================");
        out.println("                 CODECONTEXT ARCHITECTURE INTELLIGENCE DASHBOARD                ");
        out.println("================================================================================");
        out.printf(" Classes Scanned: %-10d | Invocations: %-10d | Cycles Detected: %-10d%n", classes, edges, cycles);
        out.println("--------------------------------------------------------------------------------");
        out.println(" TOP 10 ARCHITECTURAL HOTSPOTS (PageRank Authority Centrality):");
        out.printf(" %-5s | %-45s | %-10s | %-8s%n", "RANK", "CLASS NAME", "PAGERANK", "IN-DEGREE");
        out.println("-------+-----------------------------------------------+------------+----------");

        for (int i = 0; i < Math.min(10, hotspots.size()); i++) {
            HotspotMetric h = hotspots.get(i);
            String name = h.fqcn();
            if (name.length() > 45) {
                name = "..." + name.substring(name.length() - 42);
            }
            out.printf(" #%-4d | %-45s | %-10.4f | %-8d%n", h.rank(), name, h.rawScore(), h.inDegree());
        }

        out.println("--------------------------------------------------------------------------------");
        out.println(" Standalone HTML Visual Blueprint generated at: " + reportPath.toAbsolutePath());
        out.println("================================================================================");
    }
}
