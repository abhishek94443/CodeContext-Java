package com.codecontext.cli;

import com.codecontext.cli.terminal.TerminalCapabilityDetector;
import com.codecontext.core.git.GitDiffAnalyzer;
import com.codecontext.core.git.GitDiffAnalyzer.DiffAnalysisResult;
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
        name = "diff-impact",
        description = "Analyzes PR/branch diff blast radius, architectural cycle regression, and recommended test scope",
        mixinStandardHelpOptions = true
)
public class DiffImpactCommand implements Callable<Integer> {

    @Parameters(index = "0", 
        description = "Base branch or git ref to compare against (optional)", defaultValue = "")
    private String baseRef;

    @Option(names = {"--path"}, 
        description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"-d", "--depth"}, 
        description = "Maximum traversal depth for transitive callers", defaultValue = "3")
    private int depth;

    @Option(names = {"-o", "--output"}, 
        description = "Output path for GitHub PR markdown summary")
    private Path outputFile;

    @Option(names = {"--fail-on-cycles"}, 
        description = "Fail with exit code 1 if cycles exist", defaultValue = "true")
    private boolean failOnCycles;

    @Option(names = {"--no-color"}, 
        description = "Disable ANSI color escapes")
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

        // 1. Analyze Git Diff
        GitDiffAnalyzer diffAnalyzer = new GitDiffAnalyzer();
        DiffAnalysisResult diffResult = diffAnalyzer.analyzeDiff(repoPath, baseRef.isBlank() ? null : baseRef);

        // 2. Scan & Build Graph
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

        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(units, GraphBuildOptions.defaults());

        // Cycle Check
        CycleDetectionEngine cycleEngine = new CycleDetectionEngine();
        CycleReport cycleReport = cycleEngine.detectCycles(graph, CycleOptions.defaults());

        // 3. Match modified files to vertices
        Set<TypeVertex> touchedVertices = new LinkedHashSet<>();
        for (String modFile : diffResult.modifiedFiles()) {
            String normalizedMod = modFile.replace('\\', '/');
            for (TypeVertex v : graph.vertexSet()) {
                if (v.filePath().isPresent()) {
                    String vPath = v.filePath().get().toString().replace('\\', '/');
                    if (vPath.endsWith(normalizedMod) || normalizedMod.endsWith(vPath)) {
                        touchedVertices.add(v);
                    }
                }
            }
        }

        // If no Git diff or no modified files found, check if working tree had no changes
        if (touchedVertices.isEmpty() && diffResult.modifiedFiles().isEmpty()) {
            out.println("No modified Java files detected in repository diff.");
            out.println("Architectural check: CLEAN (0 touched classes).");
            return CliExitCode.SUCCESS.getCode();
        }

        // 4. Compute Blast Radius for Touched Classes
        PageRankEngine prEngine = new PageRankEngine();
        List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());
        Map<String, HotspotMetric> hotspotMap = new HashMap<>();
        for (HotspotMetric h : hotspots) {
            hotspotMap.put(h.fqcn(), h);
        }

        BlastRadiusEngine blastEngine = new BlastRadiusEngine();
        Set<String> aggregateImpactedCallers = new LinkedHashSet<>();
        Set<String> recommendedTests = new TreeSet<>();
        Map<TypeVertex, ImpactAnalysisResult> impacts = new LinkedHashMap<>();

        for (TypeVertex v : touchedVertices) {
            ImpactAnalysisResult res = blastEngine.analyzeImpact(v.fqcn(), graph, hotspotMap, depth);
            impacts.put(v, res);
            aggregateImpactedCallers.addAll(res.transitiveCallerDepths().keySet());
            recommendedTests.addAll(res.recommendedTestScope());

            // Suggest matching test files by simple name convention
            String expectedTest = v.simpleName() + "Test";
            for (TypeVertex cand : graph.vertexSet()) {
                if (cand.simpleName().equals(expectedTest)) {
                    recommendedTests.add(cand.fqcn());
                }
            }
        }

        // 5. Build Markdown PR Report
        StringBuilder md = new StringBuilder();
        md.append("### CodeContext Pull Request Blast Radius & Architecture Check\n\n");
        md.append("| Metric | Value |\n");
        md.append("|---|---|\n");

        boolean hasCycles = cycleReport.hasCycles();
        String statusStr = hasCycles ? "Blocked (Cycles Detected)" : "Passed (Clean)";
        md.append("| **Status** | ").append(statusStr).append(" |\n");
        md.append("| **Modified Classes Touched** | `").append(touchedVertices.size()).append("` |\n");
        md.append("| **Total Impacted Callers (Blast Radius)** | `").append(aggregateImpactedCallers.size()).append("` |\n");

        String cycleStr = hasCycles ? "`" + cycleReport.cycles().size() + "` cycle(s) detected" : "`0` (Clean)";
        md.append("| **Architectural Cycles** | ").append(cycleStr).append(" |\n\n");

        md.append("#### Touched Classes & Blast Radius\n");
        if (touchedVertices.isEmpty()) {
            md.append("- *No Java classes mapped to modified files.*\n");
        } else {
            for (Map.Entry<TypeVertex, ImpactAnalysisResult> entry : impacts.entrySet()) {
                TypeVertex v = entry.getKey();
                ImpactAnalysisResult res = entry.getValue();
                md.append("- `").append(v.fqcn()).append("`\n");
                md.append("  - Direct Callers (In-degree: ").append(res.directCallers().size()).append(")\n");
                md.append("  - Transitive Blast Radius: `").append(res.transitiveCallerDepths().size()).append("` callers\n");
            }
        }
        md.append("\n");

        md.append("#### Recommended Test Scope\n");
        if (recommendedTests.isEmpty()) {
            md.append("- *No specific unit or integration test suites identified.*\n");
        } else {
            for (String t : recommendedTests) {
                md.append("- `").append(t).append("`\n");
            }
        }

        String markdownContent = md.toString();

        // Print to out
        out.println(markdownContent);

        // If --output specified, write file
        if (outputFile != null) {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            Files.writeString(outputFile, markdownContent);
            out.printf("PR Summary written to: %s%n", outputFile);
        }

        if (hasCycles && failOnCycles) {
            err.printf("Architecture violation: %d circular dependency cycle(s) detected!%n", cycleReport.cycles().size());
            return CliExitCode.VIOLATION.getCode();
        }

        return CliExitCode.SUCCESS.getCode();
    }
}
