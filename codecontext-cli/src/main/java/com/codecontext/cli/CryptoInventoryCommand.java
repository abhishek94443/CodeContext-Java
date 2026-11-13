package com.codecontext.cli;

import com.codecontext.cli.terminal.TerminalCapabilityDetector;
import com.codecontext.core.security.SecurityAnalysisEngine;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.inventory.CryptoAsset;
import com.codecontext.core.security.inventory.CryptoAssetUsage;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoInventoryEngine;
import com.codecontext.core.security.rule.SecurityRuleRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI subcommand: codecontext crypto inventory [PATH] [OPTIONS]
 * Discovers and displays a normalized inventory of all cryptographic algorithms and APIs used.
 */
@Command(
        name = "inventory",
        description = "Extracts and normalizes cryptographic assets across a Java repository",
        mixinStandardHelpOptions = true
)
public class CryptoInventoryCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"-o", "--output"}, description = "Target output file path")
    private Path outputFile;

    @Option(names = {"--format"}, description = "Output format (text, json)", defaultValue = "text")
    private String format;

    @Option(names = {"--include-tests"}, description = "Include test source files in inventory")
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

        // Run engine with empty rule registry: inventory scans all files for API usages without evaluating vulnerability rules
        SecurityAnalysisEngine engine = new SecurityAnalysisEngine(SecurityRuleRegistry.empty());
        SecurityAnalysisResult result = engine.analyze(resolvedPath, null, includeTests);

        CryptoInventoryEngine inventoryEngine = new CryptoInventoryEngine();
        CryptoInventory inventory = inventoryEngine.buildInventory(result.cryptoUsages(), resolvedPath);

        String resolvedFormat = format.toLowerCase().trim();
        if (outputFile != null && outputFile.toString().endsWith(".json")) {
            resolvedFormat = "json";
        }

        if ("json".equals(resolvedFormat)) {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            ObjectNode root = mapper.createObjectNode();

            ObjectNode summary = root.putObject("summary");
            summary.put("filesScanned", result.filesScanned());
            summary.put("filesWithCrypto", inventory.summary().filesWithCrypto());
            summary.put("totalObservations", inventory.summary().totalObservations());
            summary.put("uniqueAssets", inventory.summary().uniqueAssets());
            summary.put("resolvedAssets", inventory.summary().resolvedAssets());
            summary.put("unresolvedObservations", inventory.summary().unresolvedObservations());

            ArrayNode assetsArr = root.putArray("assets");
            for (CryptoAsset asset : inventory.assets()) {
                ObjectNode an = assetsArr.addObject();
                an.put("assetId", asset.assetId());
                an.put("name", asset.name());
                an.put("algorithmFamily", asset.algorithmFamily());
                an.put("primitiveType", asset.primitiveType().name());
                asset.mode().ifPresent(m -> an.put("mode", m));
                asset.padding().ifPresent(p -> an.put("padding", p));
                an.put("usageCount", asset.usageCount());
                an.put("isResolved", asset.isResolved());
                an.put("confidence", asset.confidence().name());

                ArrayNode usagesArr = an.putArray("usages");
                for (CryptoAssetUsage u : asset.usages()) {
                    ObjectNode un = usagesArr.addObject();
                    un.put("filePath", u.filePath().toString().replace('\\', '/'));
                    un.put("line", u.location().startLine());
                    un.put("column", u.location().startColumn());
                    un.put("apiOwner", u.apiOwner().name());
                    un.put("methodName", u.methodName());
                    un.put("originalArgument", u.originalArgument());
                    un.put("status", u.argumentResolution().status().name());
                }
            }

            String jsonStr = mapper.writeValueAsString(root);
            if (outputFile != null) {
                Path target = outputFile.isAbsolute() ? outputFile : resolvedPath.resolve(outputFile);
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, jsonStr, StandardCharsets.UTF_8);
                out.printf("Cryptographic inventory JSON generated: %s (%d assets)%n",
                        target.toAbsolutePath(), inventory.assets().size());
            } else {
                out.println(jsonStr);
            }
        } else {
            // Text table output
            boolean ansi = TerminalCapabilityDetector.isAnsiSupported(noColor);
            String bold = ansi ? "\u001B[1m" : "";
            String reset = ansi ? "\u001B[0m" : "";

            out.println("================================================================================");
            out.printf(" %sCodeContext Cryptographic Asset Inventory%s%n", bold, reset);
            out.println("================================================================================");
            out.printf(" Repository Path        : %s%n", resolvedPath);
            out.printf(" Files Scanned          : %d%n", result.filesScanned());
            out.printf(" Files with Crypto      : %d%n", inventory.summary().filesWithCrypto());
            out.printf(" Total Observations     : %d%n", inventory.summary().totalObservations());
            out.printf(" Unique Crypto Assets   : %d%n", inventory.summary().uniqueAssets());
            out.printf(" Unresolved Observations: %d%n", inventory.summary().unresolvedObservations());
            out.println("================================================================================");
            out.printf(" %-32s %-14s %-16s %-8s %s%n", "Algorithm / Asset", "Family", "Primitive", "Usages", "Status");
            out.println("--------------------------------------------------------------------------------");

            if (inventory.assets().isEmpty()) {
                out.println(" No cryptographic API usages observed in scanned files.");
            } else {
                for (CryptoAsset a : inventory.assets()) {
                    String status = a.isResolved() ? "RESOLVED" : "UNRESOLVED";
                    out.printf(" %-32s %-14s %-16s %-8d %s%n",
                            truncate(a.name(), 32),
                            truncate(a.algorithmFamily(), 14),
                            truncate(a.primitiveType().name(), 16),
                            a.usageCount(),
                            status);
                }
            }
            out.println("================================================================================");
        }

        if (result.hasErrors()) {
            for (String errStr : result.analysisErrors()) {
                err.printf("Inventory diagnostic: %s%n", errStr);
            }
        }

        return CliExitCode.SUCCESS.getCode();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }
}

