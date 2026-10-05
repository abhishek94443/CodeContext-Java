package com.codecontext.cli;

import com.codecontext.core.git.GitEvolutionMetric;
import com.codecontext.core.git.GitHistoryProvider;
import com.codecontext.core.git.GitHistoryProviderFactory;
import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.analytics.*;
import com.codecontext.core.graph.export.HtmlReportPayload;
import com.codecontext.cli.export.SarifExporter;
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
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "sarif",
        description = "Emits architectural cycles and high-risk hotspots in OASIS SARIF 2.1.0 format"
)
public class SarifCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"-o", "--output"}, description = "Target output SARIF file path", defaultValue = "codecontext.sarif")
    private Path outputFile;

    @Option(names = {"--include-tests"}, description = "Include test classes in SARIF analysis")
    private boolean includeTests;

    @picocli.CommandLine.Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository path does not exist: " + repoPath);
        }

        PrintWriter out = spec.commandLine().getOut();

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

        // 2. Build graph & metrics
        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(units, GraphBuildOptions.defaults());

        PageRankEngine prEngine = new PageRankEngine();
        List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());

        CycleDetectionEngine cycleEngine = new CycleDetectionEngine();
        CycleReport cycleReport = cycleEngine.detectCycles(graph, CycleOptions.defaults());

        GitHistoryProvider gitProvider = GitHistoryProviderFactory.createProvider(repoPath);
        boolean gitAvailable = gitProvider.isAvailable();
        Map<String, GitEvolutionMetric> gitMetrics = gitAvailable ? gitProvider.extractMetrics(repoPath, 90) : Map.of();

        CompositeRiskEngine riskEngine = new CompositeRiskEngine();
        List<CompositeRiskScore> riskScores = riskEngine.calculate(hotspots, cycleReport, gitMetrics, gitAvailable);

        Map<String, TypeDefinition> typeDefs = new HashMap<>();
        for (TypeDefinition t : symbolTable.allTypes()) {
            typeDefs.put(t.fqcn(), t);
        }

        HtmlReportPayload payload = new HtmlReportPayload(
                graph, hotspots, cycleReport, riskScores, typeDefs, gitMetrics, hierarchyIndex
        );

        // 3. Export SARIF
        Path targetOutput = outputFile.isAbsolute() ? outputFile : repoPath.resolve(outputFile);
        SarifExporter exporter = new SarifExporter();
        exporter.export(payload, repoPath, targetOutput);

        int findings = cycleReport.cycles().size() + (int) riskScores.stream()
                .filter(s -> s.level() == com.codecontext.core.graph.risk.RiskLevel.CRITICAL || s.level() == com.codecontext.core.graph.risk.RiskLevel.HIGH)
                .count();

        out.printf("Successfully generated OASIS SARIF 2.1.0 report at: %s (%d findings)%n",
                targetOutput.toAbsolutePath(), findings);

        return CliExitCode.SUCCESS.getCode();
    }
}
