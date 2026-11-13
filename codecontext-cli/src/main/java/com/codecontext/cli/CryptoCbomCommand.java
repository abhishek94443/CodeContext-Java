package com.codecontext.cli;

import com.codecontext.cli.export.cbom.CycloneDxCbomExporter;
import com.codecontext.core.security.SecurityAnalysisEngine;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoInventoryEngine;
import com.codecontext.core.security.rule.SecurityRuleRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI subcommand: codecontext crypto cbom [PATH] [OPTIONS]
 * Generates an official CycloneDX 1.6 Cryptography Bill of Materials (CBOM) JSON document.
 */
@Command(
        name = "cbom",
        description = "Generates a CycloneDX 1.6 Cryptography Bill of Materials (CBOM) JSON document",
        mixinStandardHelpOptions = true
)
public class CryptoCbomCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"-o", "--output"}, description = "Target CBOM output file path", defaultValue = "crypto-bom.json")
    private Path outputFile;

    @Option(names = {"--include-tests"}, description = "Include test source files in CBOM")
    private boolean includeTests;

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

        SecurityAnalysisEngine engine = new SecurityAnalysisEngine(SecurityRuleRegistry.empty());
        SecurityAnalysisResult result = engine.analyze(resolvedPath, null, includeTests);

        CryptoInventoryEngine inventoryEngine = new CryptoInventoryEngine();
        CryptoInventory inventory = inventoryEngine.buildInventory(result.cryptoUsages(), resolvedPath);

        Path targetOutput = outputFile != null
                ? (outputFile.isAbsolute() ? outputFile : resolvedPath.resolve(outputFile))
                : resolvedPath.resolve("crypto-bom.json");

        CycloneDxCbomExporter exporter = new CycloneDxCbomExporter();
        exporter.exportCbom(inventory, resolvedPath, targetOutput);

        out.printf("CycloneDX 1.6 Cryptography Bill of Materials (CBOM) generated: %s (%d assets)%n",
                targetOutput.toAbsolutePath(), inventory.assets().size());

        if (result.hasErrors()) {
            for (String errStr : result.analysisErrors()) {
                err.printf("CBOM diagnostic: %s%n", errStr);
            }
        }

        return CliExitCode.SUCCESS.getCode();
    }
}

