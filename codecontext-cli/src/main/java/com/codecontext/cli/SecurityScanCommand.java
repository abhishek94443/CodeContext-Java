package com.codecontext.cli;

import com.codecontext.cli.export.SarifExporter;
import com.codecontext.cli.terminal.TerminalCapabilityDetector;
import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.SecurityAnalysisEngine;
import com.codecontext.core.security.SecurityAnalysisResult;
import com.codecontext.core.security.crypto.CryptoApiUsage;
import com.codecontext.core.security.rule.DemonstrationSecurityRule;
import com.codecontext.core.security.rule.SecurityRuleRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI subcommand: codecontext security scan [PATH] [OPTIONS]
 * Executes static security and cryptographic rules against a Java repository.
 */
@Command(
        name = "scan",
        description = "Scans Java codebase for cryptographic assets, security rules, and compliance",
        mixinStandardHelpOptions = true
)
public class SecurityScanCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path to analyze", defaultValue = ".")
    private Path repoPath;

    @Option(names = {"-o", "--output"}, description = "Target output file path (e.g. results.sarif)")
    private Path outputFile;

    @Option(names = {"--format"}, description = "Output format (table, json, sarif)", defaultValue = "table")
    private String format;

    @Option(names = {"--include-tests"}, description = "Include test source files in analysis")
    private boolean includeTests;

    @Option(names = {"--fail-on-findings"}, description = "Fail with exit code 1 if findings exist")
    private boolean failOnFindings;

    @Option(names = {"--demo-rule"}, description = "Enable demonstration AST rule for architectural verification")
    private boolean enableDemoRule;

    @Option(names = {"--no-default-rules"}, description = "Disable standard cryptographic security rules")
    private boolean noDefaultRules;

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

        // 1. Configure Rule Registry
        SecurityRuleRegistry registry;
        if (enableDemoRule) {
            registry = new SecurityRuleRegistry();
            registry.register(new DemonstrationSecurityRule());
        } else if (noDefaultRules) {
            registry = SecurityRuleRegistry.empty();
        } else {
            registry = SecurityRuleRegistry.defaultRegistry();
        }

        // 2. Execute Analysis Engine
        SecurityAnalysisEngine engine = new SecurityAnalysisEngine(registry);
        SecurityAnalysisResult result = engine.analyze(resolvedPath, null, includeTests);

        List<Finding> findings = result.findings();
        Collection<RuleMetadata> activeRules = result.activeRules();
        List<CryptoApiUsage> cryptoUsages = result.cryptoUsages();

        // 3. Format and Output Results
        String resolvedFormat = format.toLowerCase().trim();
        if (outputFile != null) {
            String outStr = outputFile.toString().toLowerCase();
            if (outStr.endsWith(".sarif")) {
                resolvedFormat = "sarif";
            } else if (outStr.endsWith(".html") || outStr.endsWith(".htm")) {
                resolvedFormat = "html";
            }
        }

        switch (resolvedFormat) {
            case "html" -> {
                Path targetOutput = outputFile != null
                        ? (outputFile.isAbsolute() ? outputFile : resolvedPath.resolve(outputFile))
                        : resolvedPath.resolve("codecontext-security.html");

                com.codecontext.core.security.inventory.CryptoInventoryEngine invEngine =
                        new com.codecontext.core.security.inventory.CryptoInventoryEngine();
                com.codecontext.core.security.inventory.CryptoInventory inventory =
                        invEngine.buildInventory(cryptoUsages, resolvedPath);

                String repoName = resolvedPath.getFileName() != null ? resolvedPath.getFileName().toString() : "root";
                com.codecontext.cli.export.security.SecurityReportPayload payload =
                        new com.codecontext.cli.export.security.SecurityReportPayload(
                                repoName,
                                resolvedPath,
                                java.time.Instant.now(),
                                "2.0.0",
                                result,
                                inventory,
                                activeRules
                        );

                com.codecontext.cli.export.security.SecurityHtmlReportExporter exporter =
                        new com.codecontext.cli.export.security.SecurityHtmlReportExporter();
                exporter.export(payload, targetOutput);
                out.printf("Security HTML dashboard generated: %s (%d findings, %d crypto assets)%n",
                        targetOutput.toAbsolutePath(), findings.size(), inventory.assets().size());
            }
            case "sarif" -> {
                Path targetOutput = outputFile != null
                        ? (outputFile.isAbsolute() ? outputFile : resolvedPath.resolve(outputFile))
                        : resolvedPath.resolve("security-results.sarif");

                SarifExporter exporter = new SarifExporter();
                exporter.exportFindings(findings, activeRules, resolvedPath, targetOutput);
                out.printf("Security SARIF 2.1.0 report generated: %s (%d findings)%n",
                        targetOutput.toAbsolutePath(), findings.size());
            }
            case "json" -> {
                ObjectMapper mapper = new ObjectMapper();
                ArrayNode arrayNode = mapper.createArrayNode();
                for (Finding f : findings) {
                    ObjectNode fNode = arrayNode.addObject();
                    fNode.put("ruleId", f.ruleId());
                    fNode.put("ruleName", f.ruleName());
                    fNode.put("category", f.category().name());
                    fNode.put("severity", f.severity().name());
                    fNode.put("message", f.message());
                    f.filePath().ifPresent(p -> fNode.put("filePath", p.toString().replace('\\', '/')));
                    f.location().ifPresent(loc -> {
                        ObjectNode locNode = fNode.putObject("location");
                        locNode.put("startLine", loc.startLine());
                        locNode.put("startColumn", loc.startColumn());
                        locNode.put("endLine", loc.endLine());
                        locNode.put("endColumn", loc.endColumn());
                    });
                    fNode.put("evidence", f.evidence());
                    fNode.put("remediation", f.remediation());
                    f.cweId().ifPresent(cwe -> fNode.put("cweId", cwe));
                    fNode.put("confidence", f.confidence().name());
                    if (!f.properties().isEmpty()) {
                        ObjectNode props = fNode.putObject("properties");
                        f.properties().forEach(props::put);
                    }
                }

                String jsonStr = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode);
                if (outputFile != null) {
                    Path targetFile = outputFile.isAbsolute() ? outputFile : resolvedPath.resolve(outputFile);
                    if (targetFile.getParent() != null) {
                        Files.createDirectories(targetFile.getParent());
                    }
                    Files.writeString(targetFile, jsonStr);
                    out.printf("Security JSON report generated: %s (%d findings)%n",
                            targetFile.toAbsolutePath(), findings.size());
                } else {
                    out.println(jsonStr);
                }
            }
            default -> {
                // Table output
                boolean ansi = TerminalCapabilityDetector.isAnsiSupported(noColor);
                String bold = ansi ? "\u001B[1m" : "";
                String reset = ansi ? "\u001B[0m" : "";
                String yellow = ansi ? "\u001B[33m" : "";
                String green = ansi ? "\u001B[32m" : "";

                out.println("================================================================================");
                out.printf(" %sCodeContext Security & Cryptographic Analysis%s%n", bold, reset);
                out.println("================================================================================");
                out.printf(" Repository Path : %s%n", resolvedPath);
                out.printf(" Files Scanned   : %d%n", result.filesScanned());
                out.printf(" Active Rules    : %d%n", activeRules.size());
                out.printf(" Crypto API Usages: %d%n", cryptoUsages.size());
                out.printf(" Security Findings: %d%n", findings.size());
                out.printf(" Unresolved Usages: %d%n", result.unresolvedUsagesCount());
                out.printf(" Duration        : %d ms%n", result.durationMs());
                out.println("================================================================================");

                if (activeRules.isEmpty()) {
                    out.printf("%sStatus: No active rules. Repository security posture unassessed.%s%n", yellow, reset);
                } else if (result.hasErrors()) {
                    out.printf("%sStatus: Partial scan. Completed with %d analysis errors across %d failed files.%s%n",
                            yellow, result.analysisErrors().size(), result.failedFiles().size(), reset);
                } else if (findings.isEmpty()) {
                    out.printf("%sStatus: Analysis complete. 0 findings detected for %d active rule%s across %d scanned file%s.%s%n",
                            green, activeRules.size(), activeRules.size() > 1 ? "s" : "",
                            result.filesScanned(), result.filesScanned() > 1 ? "s" : "", reset);
                    out.println("        Coverage: Evaluated defined static rules. Absence of findings does not guarantee complete security.");
                } else {
                    out.printf("%sStatus: Findings detected. %d security finding%s identified across %d scanned file%s.%s%n",
                            yellow, findings.size(), findings.size() > 1 ? "s" : "",
                            result.filesScanned(), result.filesScanned() > 1 ? "s" : "", reset);
                }

                if (result.unresolvedUsagesCount() > 0) {
                    out.println("--------------------------------------------------------------------------------");
                    out.printf(" %sUnresolved Cryptographic Usages (Inventory Observations):%s%n", yellow, reset);
                    int idx = 1;
                    for (CryptoApiUsage u : cryptoUsages) {
                        if (!u.argumentResolution().isResolved() || !u.isOwnerConfirmed()) {
                            String locStr = resolvedPath.relativize(u.filePath().toAbsolutePath().normalize()).toString().replace('\\', '/');
                            out.printf(" [%d] %s.%s(%s) at %s:%d%n",
                                    idx++, u.owner().fqcn(), u.methodName(),
                                    u.originalArgumentExpr().isBlank() ? "<unspecified>" : u.originalArgumentExpr(),
                                    locStr, u.location().startLine());
                            out.printf("     Resolution Status: %s (%s)%n",
                                    u.argumentResolution().status(), u.argumentResolution().explanation());
                        }
                    }
                }

                if (!findings.isEmpty()) {
                    out.println("--------------------------------------------------------------------------------");
                    out.printf(" %sDetected %d Security Finding%s:%s%n",
                            yellow, findings.size(), findings.size() > 1 ? "s" : "", reset);
                    for (int i = 0; i < findings.size(); i++) {
                        Finding f = findings.get(i);
                        String locStr = f.filePath()
                                .map(p -> resolvedPath.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/'))
                                .orElse("unknown");

                        int line = f.location().map(SourceRange::startLine).orElse(1);
                        out.printf(" [%d] [%s] %s %s (%s:%d)%n",
                                i + 1, f.severity().name(), f.ruleId(), f.message(), locStr, line);
                        if (!f.evidence().isBlank()) {
                            out.printf("     Evidence   : %s%n", f.evidence());
                        }
                        if (!f.remediation().isBlank()) {
                            out.printf("     Remediation: %s%n", f.remediation());
                        }
                    }
                }
                out.println("================================================================================");
            }
        }

        if (result.hasErrors()) {
            for (String errStr : result.analysisErrors()) {
                err.printf("Analysis diagnostic: %s%n", errStr);
            }
        }

        if (failOnFindings && !findings.isEmpty()) {
            err.printf("Security Quality Gate Violation: %d findings detected!%n", findings.size());
            return CliExitCode.VIOLATION.getCode();
        }

        return CliExitCode.SUCCESS.getCode();
    }
}
