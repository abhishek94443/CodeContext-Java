package com.codecontext.cli.export;

import com.codecontext.core.graph.analytics.CyclePath;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.export.HtmlReportPayload;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.graph.risk.CompositeRiskScore;
import com.codecontext.core.graph.risk.RiskLevel;
import com.codecontext.core.model.TypeDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Exports architectural findings to standard OASIS SARIF 2.1.0 JSON format.
 * Adheres to ADR-010: Formats file URIs as repository-relative POSIX paths.
 */
public class SarifExporter {

    private static final String SARIF_SCHEMA =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";
    private static final String SARIF_VERSION = "2.1.0";

    public void export(
            HtmlReportPayload payload,
            Path repoRoot,
            Path outputPath
    ) throws IOException {
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(repoRoot, "repoRoot cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        String sarifJson = generateSarifJson(payload, repoRoot);
        Files.writeString(outputPath, sarifJson, StandardCharsets.UTF_8);
    }

    public String generateSarifJson(HtmlReportPayload payload, Path repoRoot) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", SARIF_SCHEMA);
        root.put("version", SARIF_VERSION);

        ArrayNode runs = root.putArray("runs");
        ObjectNode run = runs.addObject();

        // Tool Driver
        ObjectNode tool = run.putObject("tool");
        ObjectNode driver = tool.putObject("driver");
        driver.put("name", "CodeContext");
        driver.put("version", "2.0.0");
        driver.put("informationUri", "https://github.com/codecontext/codecontext");

        ArrayNode rules = driver.putArray("rules");

        // Rule CC001: ArchitecturalCycle
        ObjectNode ruleCycle = rules.addObject();
        ruleCycle.put("id", "CC001");
        ruleCycle.put("name", "ArchitecturalCycle");
        ruleCycle.putObject("shortDescription").put("text", "Architectural circular dependency loop detected.");
        ruleCycle.putObject("fullDescription").put("text", "Mutually dependent component cycles hinder modularity, test isolation, and refactoring safety.");
        ruleCycle.putObject("defaultConfiguration").put("level", "error");

        // Rule CC002: HighRiskHotspot
        ObjectNode ruleHotspot = rules.addObject();
        ruleHotspot.put("id", "CC002");
        ruleHotspot.put("name", "HighRiskHotspot");
        ruleHotspot.putObject("shortDescription").put("text", "High PageRank authority combined with high churn.");
        ruleHotspot.putObject("fullDescription").put("text", "Central architectural hubs undergoing frequent modification pose severe regression risk.");
        ruleHotspot.putObject("defaultConfiguration").put("level", "warning");

        ArrayNode results = run.putArray("results");

        // Map Cycles to CC001
        CycleReport cycleReport = payload.cycleReport();
        Map<String, TypeDefinition> typeDefs = payload.typeDefinitions();
        Map<String, TypeVertex> vertexMap = new HashMap<>();
        for (TypeVertex v : payload.graph().vertexSet()) {
            vertexMap.put(v.fqcn(), v);
        }

        if (cycleReport != null) {
            for (CyclePath cycle : cycleReport.cycles()) {
                ObjectNode res = results.addObject();
                res.put("ruleId", "CC001");
                res.put("level", "error");
                res.putObject("message").put("text", "Architectural cycle detected: " + String.join(" -> ", cycle.fqcns()));

                String firstFqcn = cycle.fqcns().isEmpty() ? "" : cycle.fqcns().get(0);
                Path sourcePath = resolveSourcePath(firstFqcn, typeDefs, vertexMap);
                addLocation(res, repoRoot, sourcePath, 1, 1);
            }
        }

        // Map High / Critical Hotspots to CC002
        Map<String, CompositeRiskScore> riskMap = new HashMap<>();
        for (CompositeRiskScore score : payload.riskScores()) {
            riskMap.put(score.fqcn(), score);
        }

        for (HotspotMetric hotspot : payload.hotspots()) {
            CompositeRiskScore risk = riskMap.get(hotspot.fqcn());
            if (risk != null && (risk.level() == RiskLevel.CRITICAL || risk.level() == RiskLevel.HIGH)) {
                ObjectNode res = results.addObject();
                res.put("ruleId", "CC002");
                res.put("level", "warning");
                res.putObject("message").put("text", String.format(
                        "High-risk architectural hotspot: %s (PageRank: %.4f, In-degree: %d, Risk: %s)",
                        hotspot.fqcn(), hotspot.rawScore(), hotspot.inDegree(), risk.level().name()
                ));

                Path sourcePath = resolveSourcePath(hotspot.fqcn(), typeDefs, vertexMap);
                addLocation(res, repoRoot, sourcePath, 1, 1);
            }
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SARIF JSON", e);
        }
    }

    private Path resolveSourcePath(String fqcn, Map<String, TypeDefinition> typeDefs, Map<String, TypeVertex> vertexMap) {
        if (typeDefs != null && typeDefs.containsKey(fqcn)) {
            return typeDefs.get(fqcn).sourceFilePath();
        }
        if (vertexMap != null && vertexMap.containsKey(fqcn)) {
            return vertexMap.get(fqcn).filePath().orElse(null);
        }
        return null;
    }

    private void addLocation(ObjectNode resultNode, Path repoRoot, Path sourcePath, int line, int column) {
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
        ObjectNode region = phys.putObject("region");
        region.put("startLine", Math.max(1, line));
        region.put("startColumn", Math.max(1, column));
    }
}
