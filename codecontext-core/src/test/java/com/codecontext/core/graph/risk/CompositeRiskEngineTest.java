package com.codecontext.core.graph.risk;

import com.codecontext.core.git.GitEvolutionMetric;
import com.codecontext.core.graph.analytics.CyclePath;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.model.HotspotMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CompositeRiskEngineTest {

    private final CompositeRiskEngine engine = new CompositeRiskEngine();

    @Test
    @DisplayName("FTC-S2.3-003: Calculate composite risk with Git available and dynamic rebalancing without Git")
    void should_calculate_composite_risk_scores() {
        HotspotMetric hotspot = new HotspotMetric(
                "com.example.PaymentProcessor",
                Path.of("src/PaymentProcessor.java"),
                0.05,
                90.0,
                5,
                2,
                1
        );

        CyclePath cyclePath = new CyclePath(List.of("com.example.PaymentProcessor", "com.example.Other", "com.example.PaymentProcessor"));
        CycleReport cycleReport = new CycleReport(List.of(cyclePath), 1, false);

        GitEvolutionMetric gitMetric = new GitEvolutionMetric(
                "src/PaymentProcessor.java",
                10,
                50,
                10,
                1,
                1.0 // 100% bus factor risk
        );

        // Mode 1: Git available
        List<CompositeRiskScore> withGit = engine.calculate(
                List.of(hotspot),
                cycleReport,
                Map.of("src/PaymentProcessor.java", gitMetric),
                true
        );

        assertThat(withGit).hasSize(1);
        CompositeRiskScore scoreWithGit = withGit.get(0);
        // PR=90 (0.40) -> 36.0, Churn=100 (0.30) -> 30.0, Cycle=100 (0.20) -> 20.0, Bus=100 (0.10) -> 10.0 => 96.0
        assertThat(scoreWithGit.overallScore()).isCloseTo(96.0, within(1.0));
        assertThat(scoreWithGit.level()).isEqualTo(RiskLevel.CRITICAL);

        // Mode 2: Git absent (Dynamic rebalancing: 0.60 PR + 0.40 Cycle)
        List<CompositeRiskScore> withoutGit = engine.calculate(
                List.of(hotspot),
                cycleReport,
                Map.of(),
                false
        );

        assertThat(withoutGit).hasSize(1);
        CompositeRiskScore scoreNoGit = withoutGit.get(0);
        // (0.60 * 90.0) + (0.40 * 100.0) = 54.0 + 40.0 = 94.0
        assertThat(scoreNoGit.overallScore()).isCloseTo(94.0, within(1e-4));
        assertThat(scoreNoGit.level()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    @DisplayName("FTC-S2.3-006: Composite risk level tier categorization")
    void should_categorize_tiers_accurately() {
        assertThat(RiskLevel.fromScore(10.0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(25.0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(26.0)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(50.0)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(51.0)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(75.0)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(76.0)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(RiskLevel.fromScore(100.0)).isEqualTo(RiskLevel.CRITICAL);
    }
}
