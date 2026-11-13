package com.codecontext.cli;

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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "prepare",
        description = "Synthesizes an advisory EARS change-safety plan for a target class",
        mixinStandardHelpOptions = true
)
public class PrepareCommand implements Callable<Integer> {

    @Option(names = {"--target", "-t"}, required = true, 
        description = "Target class name (simple name or FQCN)")
    private String targetClass;

    @Option(names = {"--path"}, 
        description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"--output", "--plan", "-o"}, 
        description = "Plan output file path", defaultValue = ".codecontext-plan.json")
    private Path planFile;

    @picocli.CommandLine.Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository path does not exist: " + repoPath);
        }

        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        // 1. Scan & Build Graph
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

        // 2. Resolve Target
        TypeVertex target = graph.vertexSet().stream()
                .filter(v -> v.fqcn().equals(targetClass) || v.simpleName().equals(targetClass))
                .findFirst()
                .orElse(null);

        if (target == null) {
            err.printf("Class not found in symbol index: %s%n", targetClass);
            return CliExitCode.BAD_ARGS.getCode();
        }

        // 3. Compute blast radius
        PageRankEngine prEngine = new PageRankEngine();
        List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());
        Map<String, HotspotMetric> hotspotMap = new HashMap<>();
        for (HotspotMetric h : hotspots) {
            hotspotMap.put(h.fqcn(), h);
        }

        BlastRadiusEngine blastEngine = new BlastRadiusEngine();
        ImpactAnalysisResult impact = blastEngine.analyzeImpact(target.fqcn(), graph, hotspotMap, 3);

        // 4. Synthesize allowed files and EARS rules
        Set<String> allowedFiles = new LinkedHashSet<>();
        Path absRepo = repoPath.toAbsolutePath().normalize();
        if (target.filePath().isPresent()) {
            Path rel = absRepo.relativize(target.filePath().get().toAbsolutePath().normalize());
            allowedFiles.add(rel.toString().replace('\\', '/'));
        }

        // Add test files
        String testSimpleName = target.simpleName() + "Test";
        for (TypeVertex v : graph.vertexSet()) {
            if (v.simpleName().equals(testSimpleName) && v.filePath().isPresent()) {
                Path rel = absRepo.relativize(v.filePath().get().toAbsolutePath().normalize());
                allowedFiles.add(rel.toString().replace('\\', '/'));
            }
        }

        List<String> earsRules = new ArrayList<>();
        earsRules.add("WHEN modifying " + target.simpleName() + ", THE SYSTEM SHALL NOT break public API signatures.");
        earsRules.add("WHILE modifying " + target.simpleName() + ", ONLY files in allowedModifiedFiles SHALL be changed.");
        if (!impact.directCallers().isEmpty()) {
            earsRules.add("IF " + target.simpleName() + " is invoked by " + impact.directCallers().size() + " caller(s), THE SYSTEM SHALL preserve behavioral contract.");
        }

        // 5. Build JSON
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode plan = mapper.createObjectNode();
        plan.put("schemaVersion", "1.0.0");
        plan.put("targetClass", target.fqcn());

        ArrayNode allowedArr = plan.putArray("allowedModifiedFiles");
        for (String f : allowedFiles) allowedArr.add(f);

        ArrayNode callersArr = plan.putArray("impactedCallers");
        for (String c : impact.transitiveCallerDepths().keySet()) callersArr.add(c);

        ArrayNode rulesArr = plan.putArray("earsRules");
        for (String r : earsRules) rulesArr.add(r);

        ArrayNode testsArr = plan.putArray("recommendedTests");
        for (String t : impact.recommendedTestScope()) testsArr.add(t);

        Path outPlan = planFile.isAbsolute() ? planFile : repoPath.resolve(planFile);
        if (outPlan.getParent() != null) {
            Files.createDirectories(outPlan.getParent());
        }
        Files.writeString(outPlan, mapper.writeValueAsString(plan));

        out.printf("Advisory EARS change-safety plan generated: %s%n", outPlan);
        out.printf("Target Class: %s (Allowed files: %d, EARS rules: %d)%n",
                target.fqcn(), allowedFiles.size(), earsRules.size());

        return CliExitCode.SUCCESS.getCode();
    }
}
