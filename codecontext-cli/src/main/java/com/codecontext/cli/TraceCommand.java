package com.codecontext.cli;

import com.codecontext.cli.terminal.TerminalCapabilityDetector;
import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.analytics.PathTraceEngine;
import com.codecontext.core.graph.analytics.PathTraceEngine.PathTraceResult;
import com.codecontext.core.graph.analytics.PathTraceEngine.TraceStep;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.InvocationDetail;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "trace",
        description = "Finds and traces the shortest execution call chain between two components",
        mixinStandardHelpOptions = true
)
public class TraceCommand implements Callable<Integer> {

    @Parameters(index = "0", 
        description = "Source class name (simple name or FQCN)")
    private String from;

    @Parameters(index = "1", 
        description = "Target class name (simple name or FQCN)")
    private String to;

    @Option(names = {"--path"}, 
        description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"--max-hops"}, 
        description = "Maximum call-chain hops to search", defaultValue = "10")
    private int maxHops;

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

        // 1. Scan & Resolve Graph
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

        // 2. Run PathTraceEngine
        PathTraceEngine engine = new PathTraceEngine();
        PathTraceResult result = engine.tracePath(graph, from, to, maxHops);

        // Check if missing class
        if (result.message().contains("not found in graph")) {
            err.printf("Class not found in symbol index: %s%n", result.message());
            return CliExitCode.BAD_ARGS.getCode();
        }

        boolean ansi = TerminalCapabilityDetector.isAnsiSupported(noColor);
        String cyan = ansi ? "\u001B[36m" : "";
        String yellow = ansi ? "\u001B[33m" : "";
        String green = ansi ? "\u001B[32m" : "";
        String bold = ansi ? "\u001B[1m" : "";
        String reset = ansi ? "\u001B[0m" : "";

        if (!result.pathFound()) {
            out.printf("%s%s%s%n", yellow, result.message(), reset);
            return CliExitCode.SUCCESS.getCode();
        }

        out.println("================================================================================");
        out.printf(" %sExecution Path Trace: %s -> %s (%d %s)%s%n",
                bold,
                result.startVertex().simpleName(),
                result.endVertex().simpleName(),
                result.hopCount(),
                result.hopCount() == 1 ? "hop" : "hops",
                reset);
        out.println("================================================================================");

        for (TraceStep step : result.steps()) {
            out.printf(" %sStep %d:%s %s%s%s%n",
                    cyan,
                    step.stepNumber(),
                    reset,
                    bold,
                    step.source().fqcn(),
                    reset);

            if (step.callDetails().isEmpty()) {
                out.printf("   +-- %sreferences%s %s%n", yellow, reset, step.target().fqcn());
            } else {
                for (InvocationDetail call : step.callDetails()) {
                    int line = call.range() != null ? call.range().startLine() : 0;
                    String lineInfo = line > 0 ? " [Line " + line + "]" : "";
                    out.printf("   +-- calls %s%s.%s()%s%s%n",
                            green,
                            step.target().simpleName(),
                            call.targetMethodName(),
                            reset,
                            lineInfo);
                }
            }
        }
        out.printf(" %sFinal Target:%s %s%s%s%n",
                cyan,
                reset,
                bold,
                result.endVertex().fqcn(),
                reset);
        out.println("================================================================================");

        return CliExitCode.SUCCESS.getCode();
    }
}
