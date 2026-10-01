package com.codecontext.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedCompilationUnitTest {

    @Test
    @DisplayName("UTC-S1.3-US02-010: ResolutionMetrics ratio calculation when 100% resolved")
    void testMetricsRatio100Percent() {
        ResolutionMetrics metrics = new ResolutionMetrics(10, 5, 5, 0);
        assertThat(metrics.resolutionRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("UTC-S1.3-US02-011: ResolutionMetrics ratio calculation when partially resolved")
    void testMetricsRatioPartial() {
        ResolutionMetrics metrics = new ResolutionMetrics(10, 6, 2, 2);
        assertThat(metrics.resolutionRatio()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("UTC-S1.3-US02-012 & FTC-S1.3-009: ResolutionMetrics handles zero invocations without division by zero")
    void testMetricsZeroInvocations() {
        ResolutionMetrics metrics = new ResolutionMetrics(0, 0, 0, 0);
        assertThat(metrics.resolutionRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("UTC-S1.3-US02-013: ParsedCompilationUnit immutability and defensive copying")
    void testParsedCompilationUnitImmutability() {
        List<InvocationReference> invocations = new ArrayList<>();
        invocations.add(new InvocationReference(
                "com.example.OrderService",
                "checkout",
                "com.example.PaymentService",
                "pay",
                "pay(BigDecimal)",
                InvocationKind.METHOD_CALL,
                new SourceRange(10, 5, 10, 25),
                true
        ));

        ParsedCompilationUnit unit = new ParsedCompilationUnit(
                Path.of("OrderService.java"),
                "com.example",
                List.of("import java.util.List;"),
                List.of(),
                invocations,
                new ResolutionMetrics(1, 1, 0, 0),
                List.of()
        );

        // Attempt mutating source list
        invocations.clear();
        assertThat(unit.invocations()).hasSize(1);

        // Attempt mutating returned list
        assertThatThrownBy(() -> unit.invocations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}