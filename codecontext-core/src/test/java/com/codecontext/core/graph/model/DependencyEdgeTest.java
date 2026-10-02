package com.codecontext.core.graph.model;

import com.codecontext.core.model.SourceRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DependencyEdgeTest {

    @Test
    @DisplayName("UTC-S2.1-US01-005: should initialize edge with single call and base weight 1.0")
    void should_initialize_edge_with_single_call() {
        InvocationDetail detail = new InvocationDetail(
                "placeOrder",
                "save",
                "save(Order)",
                new SourceRange(10, 5, 10, 20)
        );

        DependencyEdge edge = new DependencyEdge(detail);

        assertThat(edge.getCallCount()).isEqualTo(1);
        assertThat(edge.getWeight()).isCloseTo(1.0, within(1e-6));
        assertThat(edge.getCallDetails()).containsExactly(detail);
    }

    @Test
    @DisplayName("UTC-S2.1-US01-006: should accumulate calls and recalculate logarithmic weight")
    void should_accumulate_calls_and_recalculate_weight() {
        InvocationDetail d1 = new InvocationDetail("m1", "t1", "t1()", new SourceRange(1, 1, 1, 10));
        InvocationDetail d2 = new InvocationDetail("m2", "t2", "t2()", new SourceRange(2, 1, 2, 10));
        InvocationDetail d3 = new InvocationDetail("m3", "t3", "t3()", new SourceRange(3, 1, 3, 10));

        DependencyEdge edge = new DependencyEdge(d1);
        edge.recordCall(d2);
        edge.recordCall(d3);

        assertThat(edge.getCallCount()).isEqualTo(3);
        // weight = log2(1 + 3) = log2(4) = 2.0
        assertThat(edge.getWeight()).isCloseTo(2.0, within(1e-6));
        assertThat(edge.getCallDetails()).hasSize(3);
    }

    @Test
    @DisplayName("UTC-S2.1-US01-007: should return immutable view of call details")
    void should_return_immutable_view() {
        InvocationDetail d1 = new InvocationDetail("m1", "t1", "t1()", new SourceRange(1, 1, 1, 10));
        DependencyEdge edge = new DependencyEdge(d1);

        assertThatThrownBy(() -> edge.getCallDetails().add(d1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("UTC-S2.1-US01-008: should reject null initial invocation detail")
    void should_reject_null_detail() {
        assertThatThrownBy(() -> new DependencyEdge(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("initialDetail");
    }
}
