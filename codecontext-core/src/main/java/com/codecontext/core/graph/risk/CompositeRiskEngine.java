package com.codecontext.core.graph.risk;

import com.codecontext.core.git.GitEvolutionMetric;
import com.codecontext.core.graph.analytics.CyclePath;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.model.HotspotMetric;

import java.util.*;

/**
 * Calculates normalized 0-100 composite risk with dynamic weight rebalancing.
 */
public class CompositeRiskEngine {

    public List<CompositeRiskScore> calculate(
            List<HotspotMetric> hotspotMetrics,
            CycleReport cycleReport,
            Map<String, GitEvolutionMetric> gitMetrics,
            boolean gitAvailable
    ) {
        if (hotspotMetrics == null || hotspotMetrics.isEmpty()) {
            return List.of();
        }

        // Set of FQCNs involved in cycles
        Set<String> cyclicFqcns = new HashSet<>();
        if (cycleReport != null && cycleReport.hasCycles()) {
            for (CyclePath path : cycleReport.cycles()) {
                cyclicFqcns.addAll(path.fqcns());
            }
        }

        // Churn max normalization
        double maxChurn = 1.0;
        if (gitAvailable && gitMetrics != null) {
            for (GitEvolutionMetric m : gitMetrics.values()) {
                double c = m.commitCount() + (m.linesAdded() + m.linesDeleted()) / 10.0;
                if (c > maxChurn) maxChurn = c;
            }
        }

        List<CompositeRiskScore> result = new ArrayList<>(hotspotMetrics.size());

        for (HotspotMetric hotspot : hotspotMetrics) {
            String fqcn = hotspot.fqcn();
            double prFactor = hotspot.percentileScore(); // already 0.0 - 100.0
            double cycleFactor = cyclicFqcns.contains(fqcn) ? 100.0 : 0.0;

            double overall;
            double churnFactor = 0.0;
            double busFactor = 0.0;

            if (gitAvailable && gitMetrics != null) {
                // Find matching git metric by path or filename
                String pathStr = hotspot.filePath().map(p -> p.toString().replace('\\', '/')).orElse("");
                GitEvolutionMetric gMetric = gitMetrics.get(pathStr);
                if (gMetric == null && !pathStr.isEmpty()) {
                    for (Map.Entry<String, GitEvolutionMetric> entry : gitMetrics.entrySet()) {
                        if (pathStr.endsWith(entry.getKey()) || entry.getKey().endsWith(pathStr)) {
                            gMetric = entry.getValue();
                            break;
                        }
                    }
                }

                if (gMetric != null) {
                    double rawChurn = gMetric.commitCount() + (gMetric.linesAdded() + gMetric.linesDeleted()) / 10.0;
                    churnFactor = Math.min(100.0, 100.0 * (rawChurn / maxChurn));
                    busFactor = gMetric.busFactorRisk() * 100.0;
                }

                // Weighted combination: 0.40 PR + 0.30 Churn + 0.20 Cycle + 0.10 BusFactor
                overall = (0.40 * prFactor) + (0.30 * churnFactor) + (0.20 * cycleFactor) + (0.10 * busFactor);
            } else {
                // Dynamic reweighting when Git is absent: 0.60 PR + 0.40 Cycle (ADR-006)
                overall = (0.60 * prFactor) + (0.40 * cycleFactor);
            }

            overall = Math.max(0.0, Math.min(100.0, overall));
            RiskLevel level = RiskLevel.fromScore(overall);

            result.add(new CompositeRiskScore(
                    fqcn,
                    hotspot.filePath(),
                    overall,
                    level,
                    prFactor,
                    churnFactor,
                    cycleFactor,
                    busFactor
            ));
        }

        result.sort(Comparator.comparingDouble(CompositeRiskScore::overallScore).reversed());
        return Collections.unmodifiableList(result);
    }
}
