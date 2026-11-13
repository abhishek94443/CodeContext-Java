package com.codecontext.cli.export;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.graph.analytics.CyclePath;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.export.HtmlReportPayload;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.graph.risk.CompositeRiskScore;
import com.codecontext.core.graph.risk.RiskLevel;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.TypeDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapts architectural graph analysis outputs (cycles and hotspots) into generalized Finding models.
 * Preserves CC001 and CC002 rule IDs and maps exact known source locations when available.
 */
public final class ArchitecturalFindingAdapter {

    public static final RuleMetadata CC001_RULE = RuleMetadata.of(
            "CC001",
            "ArchitecturalCycle",
            FindingCategory.ARCHITECTURE,
            FindingSeverity.HIGH,
            "Architectural circular dependency loop detected.",
            "Mutually dependent component cycles hinder modularity, test isolation, and refactoring safety."
    );

    public static final RuleMetadata CC002_RULE = RuleMetadata.of(
            "CC002",
            "HighRiskHotspot",
            FindingCategory.ARCHITECTURE,
            FindingSeverity.MEDIUM,
            "High PageRank authority combined with high churn.",
            "Central architectural hubs undergoing frequent modification pose severe regression risk."
    );

    private ArchitecturalFindingAdapter() {}

    public static Collection<RuleMetadata> architecturalRules() {
        return List.of(CC001_RULE, CC002_RULE);
    }

    public static List<Finding> toFindings(HtmlReportPayload payload) {
        if (payload == null) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        Map<String, TypeDefinition> typeDefs = payload.typeDefinitions();
        Map<String, TypeVertex> vertexMap = new HashMap<>();
        for (TypeVertex v : payload.graph().vertexSet()) {
            vertexMap.put(v.fqcn(), v);
        }

        // 1. Adapt Cycles -> CC001
        CycleReport cycleReport = payload.cycleReport();
        if (cycleReport != null) {
            for (CyclePath cycle : cycleReport.cycles()) {
                String firstFqcn = cycle.fqcns().isEmpty() ? "" : cycle.fqcns().get(0);
                Path sourcePath = resolveSourcePath(firstFqcn, typeDefs, vertexMap);
                SourceRange range = resolveRange(firstFqcn, typeDefs);

                findings.add(new Finding(
                        CC001_RULE.id(),
                        CC001_RULE.name(),
                        CC001_RULE.category(),
                        CC001_RULE.defaultSeverity(),
                        "Architectural cycle detected: " + String.join(" -> ", cycle.fqcns()),
                        Optional.ofNullable(sourcePath),
                        Optional.ofNullable(range),
                        String.join(" -> ", cycle.fqcns()),
                        "Refactor cyclic dependencies by introducing an interface or separating common abstractions.",
                        Optional.empty(),
                        AnalysisConfidence.HIGH,
                        Map.of("cycleLength", String.valueOf(cycle.length()))
                ));
            }
        }

        // 2. Adapt High/Critical Hotspots -> CC002
        Map<String, CompositeRiskScore> riskMap = new HashMap<>();
        for (CompositeRiskScore score : payload.riskScores()) {
            riskMap.put(score.fqcn(), score);
        }

        for (HotspotMetric hotspot : payload.hotspots()) {
            CompositeRiskScore risk = riskMap.get(hotspot.fqcn());
            if (risk != null && (risk.level() == RiskLevel.CRITICAL || risk.level() == RiskLevel.HIGH)) {
                Path sourcePath = resolveSourcePath(hotspot.fqcn(), typeDefs, vertexMap);
                SourceRange range = resolveRange(hotspot.fqcn(), typeDefs);

                String msg = String.format(
                        "High-risk architectural hotspot: %s (PageRank: %.4f, In-degree: %d, Risk: %s)",
                        hotspot.fqcn(), hotspot.rawScore(), hotspot.inDegree(), risk.level().name()
                );

                findings.add(new Finding(
                        CC002_RULE.id(),
                        CC002_RULE.name(),
                        CC002_RULE.category(),
                        CC002_RULE.defaultSeverity(),
                        msg,
                        Optional.ofNullable(sourcePath),
                        Optional.ofNullable(range),
                        "In-degree: " + hotspot.inDegree() + ", PageRank: " + hotspot.rawScore(),
                        "Consider decoupling this high-gravity class to reduce blast radius on modification.",
                        Optional.empty(),
                        AnalysisConfidence.HIGH,
                        Map.of("riskLevel", risk.level().name(), "pageRank", String.valueOf(hotspot.rawScore()))
                ));
            }
        }

        return findings;
    }

    private static Path resolveSourcePath(String fqcn, Map<String, TypeDefinition> typeDefs, Map<String, TypeVertex> vertexMap) {
        if (typeDefs != null && typeDefs.containsKey(fqcn)) {
            return typeDefs.get(fqcn).sourceFilePath();
        }
        if (vertexMap != null && vertexMap.containsKey(fqcn)) {
            return vertexMap.get(fqcn).filePath().orElse(null);
        }
        return null;
    }

    private static SourceRange resolveRange(String fqcn, Map<String, TypeDefinition> typeDefs) {
        if (typeDefs != null && typeDefs.containsKey(fqcn)) {
            return typeDefs.get(fqcn).range();
        }
        return null;
    }
}

