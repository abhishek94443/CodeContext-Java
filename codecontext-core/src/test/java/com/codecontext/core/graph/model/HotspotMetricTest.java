package com.codecontext.core.graph.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotspotMetricTest {

    @Test
    @DisplayName("UTC-S2.1-US02-007: should construct valid HotspotMetric")
    void should_construct_valid_metric() {
        HotspotMetric metric = new HotspotMetric(
                "com.example.OrderService",
                Path.of("src/OrderService.java"),
                0.0456,
                95.5,
                10,
                2,
                1
        );

        assertThat(metric.fqcn()).isEqualTo("com.example.OrderService");
        assertThat(metric.filePath()).contains(Path.of("src/OrderService.java"));
        assertThat(metric.rawScore()).isEqualTo(0.0456);
        assertThat(metric.percentileScore()).isEqualTo(95.5);
        assertThat(metric.inDegree()).isEqualTo(10);
        assertThat(metric.outDegree()).isEqualTo(2);
        assertThat(metric.rank()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTC-S2.1-US02-008: should reject negative rawScore")
    void should_reject_negative_raw_score() {
        assertThatThrownBy(() -> new HotspotMetric(
                "com.example.Foo",
                (Path) null,
                -0.01,
                50.0,
                1,
                1,
                1
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("rawScore");
    }

    @Test
    @DisplayName("UTC-S2.1-US02-009: should reject out-of-range percentileScore")
    void should_reject_out_of_range_percentile() {
        assertThatThrownBy(() -> new HotspotMetric(
                "com.example.Foo",
                (Path) null,
                0.1,
                101.0,
                1,
                1,
                1
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("percentileScore");

        assertThatThrownBy(() -> new HotspotMetric(
                "com.example.Foo",
                (Path) null,
                0.1,
                -1.0,
                1,
                1,
                1
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("percentileScore");
    }

    @Test
    @DisplayName("UTC-S2.1-US02-010: should reject zero or negative rank")
    void should_reject_invalid_rank() {
        assertThatThrownBy(() -> new HotspotMetric(
                "com.example.Foo",
                (Path) null,
                0.1,
                50.0,
                1,
                1,
                0
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("rank");
    }
}
