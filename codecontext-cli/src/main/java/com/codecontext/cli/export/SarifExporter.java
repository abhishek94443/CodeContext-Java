package com.codecontext.cli.export;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.graph.export.HtmlReportPayload;
import com.codecontext.core.model.SourceRange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generalized exporter for static analysis and architectural findings in OASIS SARIF 2.1.0 JSON format.
 * Fully decoupled from specific rule logic; provides deterministic ordering and exact source location mapping.
 */
public class SarifExporter {

    public static final String SARIF_SCHEMA =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";
    public static final String SARIF_VERSION = "2.1.0";
    public static final String TOOL_NAME = "CodeContext";
    public static final String TOOL_VERSION = "2.0.0";
    public static final String TOOL_INFORMATION_URI = "https://github.com/codecontext/codecontext";

    /**
     * Backward-compatible export method for architectural graph payload.
     */
    public void export(
            HtmlReportPayload payload,
            Path repoRoot,
            Path outputPath
    ) throws IOException {
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(repoRoot, "repoRoot cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");

        List<Finding> findings = ArchitecturalFindingAdapter.toFindings(payload);
        Collection<RuleMetadata> rules = ArchitecturalFindingAdapter.architecturalRules();
        exportFindings(findings, rules, repoRoot, outputPath);
    }

    /**
     * Backward-compatible SARIF JSON generation method for architectural graph payload.
     */
    public String generateSarifJson(HtmlReportPayload payload, Path repoRoot) {
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(repoRoot, "repoRoot cannot be null");

        List<Finding> findings = ArchitecturalFindingAdapter.toFindings(payload);
        Collection<RuleMetadata> rules = ArchitecturalFindingAdapter.architecturalRules();
        return generateSarifJson(findings, rules, repoRoot);
    }

    /**
     * Exports a collection of findings and rules to a SARIF 2.1.0 JSON file.
     */
    public void exportFindings(
            Collection<Finding> findings,
            Collection<RuleMetadata> rules,
            Path repoRoot,
            Path outputPath
    ) throws IOException {
        Objects.requireNonNull(findings, "findings cannot be null");
        Objects.requireNonNull(rules, "rules cannot be null");
        Objects.requireNonNull(repoRoot, "repoRoot cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        String sarifJson = generateSarifJson(findings, rules, repoRoot);
        Files.writeString(outputPath, sarifJson, StandardCharsets.UTF_8);
    }

    /**
     * Generates standard OASIS SARIF 2.1.0 JSON representing the given findings and rules.
     */
    public String generateSarifJson(
            Collection<Finding> findings,
            Collection<RuleMetadata> rules,
            Path repoRoot
    ) {
        Objects.requireNonNull(findings, "findings cannot be null");
        Objects.requireNonNull(rules, "rules cannot be null");
        Objects.requireNonNull(repoRoot, "repoRoot cannot be null");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", SARIF_SCHEMA);
        root.put("version", SARIF_VERSION);

        ArrayNode runs = root.putArray("runs");
        ObjectNode run = runs.addObject();

        // 1. Tool Driver
        ObjectNode tool = run.putObject("tool");
        ObjectNode driver = tool.putObject("driver");
        driver.put("name", TOOL_NAME);
        driver.put("version", TOOL_VERSION);
        driver.put("informationUri", TOOL_INFORMATION_URI);

        ArrayNode rulesArray = driver.putArray("rules");
        Map<String, RuleMetadata> registeredRules = new LinkedHashMap<>();

        for (RuleMetadata r : rules) {
            registeredRules.put(r.id(), r);
        }

        // Synthesize placeholder metadata for any finding with an unregistered rule ID
        for (Finding f : findings) {
            registeredRules.computeIfAbsent(f.ruleId(), id -> RuleMetadata.of(
                    id,
                    f.ruleName(),
                    f.category(),
                    f.severity(),
                    f.ruleName(),
                    f.ruleName()
            ));
        }

        for (RuleMetadata r : registeredRules.values()) {
            ObjectNode ruleNode = rulesArray.addObject();
            ruleNode.put("id", r.id());
            ruleNode.put("name", r.name());
            ruleNode.putObject("shortDescription").put("text", r.shortDescription());
            ruleNode.putObject("fullDescription").put("text", r.fullDescription());
            ruleNode.putObject("defaultConfiguration").put("level", r.defaultSeverity().toSarifLevel());

            r.helpUri().ifPresent(uri -> ruleNode.put("helpUri", uri));
            r.cweId().ifPresent(cwe -> {
                ObjectNode props = ruleNode.putObject("properties");
                props.put("cwe", cwe);
            });
        }

        // 2. Results (Deterministically Ordered)
        ArrayNode results = run.putArray("results");
        List<Finding> sortedFindings = new ArrayList<>(findings);
        Collections.sort(sortedFindings);

        for (Finding f : sortedFindings) {
            ObjectNode res = results.addObject();
            res.put("ruleId", f.ruleId());
            res.put("level", f.severity().toSarifLevel());
            res.putObject("message").put("text", f.message());

            if (f.filePath().isPresent()) {
                Path sourcePath = f.filePath().get();
                addLocation(res, repoRoot, sourcePath, f.location().orElse(null));
            }

            if (!f.properties().isEmpty()) {
                ObjectNode props = res.putObject("properties");
                f.properties().forEach(props::put);
            }
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SARIF JSON", e);
        }
    }

    private void addLocation(ObjectNode resultNode, Path repoRoot, Path sourcePath, SourceRange range) {
        if (sourcePath == null) {
            return;
        }

        String posixUri;
        try {
            Path normRepo = repoRoot.toAbsolutePath().normalize();
            Path normSource = sourcePath.toAbsolutePath().normalize();
            if (normSource.startsWith(normRepo)) {
                posixUri = normRepo.relativize(normSource).toString().replace('\\', '/');
            } else {
                posixUri = normSource.getFileName().toString();
            }
        } catch (Exception e) {
            posixUri = sourcePath.getFileName().toString();
        }

        ArrayNode locations = resultNode.putArray("locations");
        ObjectNode loc = locations.addObject();
        ObjectNode phys = loc.putObject("physicalLocation");
        phys.putObject("artifactLocation").put("uri", posixUri);

        if (range != null) {
            ObjectNode region = phys.putObject("region");
            region.put("startLine", Math.max(1, range.startLine()));
            region.put("startColumn", Math.max(1, range.startColumn()));
            region.put("endLine", Math.max(range.startLine(), range.endLine()));
            region.put("endColumn", Math.max(1, range.endColumn()));
        }
    }
}
