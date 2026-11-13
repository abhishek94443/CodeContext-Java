package com.codecontext.cli;

import com.codecontext.core.git.GitDiffAnalyzer;
import com.codecontext.core.git.GitDiffAnalyzer.DiffAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "verify",
        description = "Verifies working tree changes against an advisory change-safety plan",
        mixinStandardHelpOptions = true
)
public class VerifyCommand implements Callable<Integer> {

    @Option(names = {"--plan", "-p"}, 
        description = "Path to change safety plan", defaultValue = ".codecontext-plan.json")
    private Path planFile;

    @Option(names = {"--path"}, 
        description = "Repository path", defaultValue = ".")
    private Path repoPath;

    @picocli.CommandLine.Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository path does not exist: " + repoPath);
        }

        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        Path resolvedPlan = planFile.isAbsolute() ? planFile : repoPath.resolve(planFile);
        if (!Files.exists(resolvedPlan)) {
            err.printf("Plan file does not exist: %s%n", resolvedPlan);
            return CliExitCode.BAD_ARGS.getCode();
        }

        // 1. Parse Plan
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(resolvedPlan.toFile());
        Set<String> allowedFiles = new HashSet<>();
        if (root.has("allowedModifiedFiles")) {
            for (JsonNode n : root.get("allowedModifiedFiles")) {
                allowedFiles.add(n.asText().replace('\\', '/'));
            }
        }

        // 2. Extract Git Diff
        GitDiffAnalyzer diffAnalyzer = new GitDiffAnalyzer();
        DiffAnalysisResult diffResult = diffAnalyzer.analyzeDiff(repoPath, null);

        // 3. Verify Scopes
        List<String> violations = new ArrayList<>();
        for (String modFile : diffResult.modifiedFiles()) {
            String normalizedMod = modFile.replace('\\', '/');
            // Ignore the plan file itself if modified
            if (normalizedMod.endsWith(".codecontext-plan.json")) {
                continue;
            }

            boolean isAllowed = false;
            for (String allowed : allowedFiles) {
                if (normalizedMod.equals(allowed) || normalizedMod.endsWith(allowed) || allowed.endsWith(normalizedMod)) {
                    isAllowed = true;
                    break;
                }
            }

            if (!isAllowed) {
                violations.add(modFile);
            }
        }

        if (!violations.isEmpty()) {
            err.println("Scope violation: The following modified files are outside the authorized plan scope:");
            for (String v : violations) {
                err.printf("  [VIOLATION] %s%n", v);
            }
            return CliExitCode.VIOLATION.getCode();
        }

        out.println("Verification passed: All modified files are within authorized plan scope.");
        return CliExitCode.SUCCESS.getCode();
    }
}
