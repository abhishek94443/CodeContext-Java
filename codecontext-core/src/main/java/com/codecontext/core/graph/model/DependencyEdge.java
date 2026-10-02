package com.codecontext.core.graph.model;

import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Directed weighted edge representing one or more invocations from Caller to Callee.
 * Edge weight is calculated with logarithmic damping: weight = log2(1 + callCount).
 */
public class DependencyEdge extends DefaultWeightedEdge {

    private int callCount;
    private double calculatedWeight;
    private final List<InvocationDetail> callDetails = new ArrayList<>();

    public DependencyEdge(InvocationDetail initialDetail) {
        Objects.requireNonNull(initialDetail, "initialDetail cannot be null");
        this.callCount = 1;
        this.callDetails.add(initialDetail);
        this.calculatedWeight = 1.0; // log2(1 + 1) = log2(2) = 1.0
    }

    public synchronized void recordCall(InvocationDetail detail) {
        Objects.requireNonNull(detail, "detail cannot be null");
        this.callCount++;
        this.callDetails.add(detail);
        // weight = log2(1 + callCount) = ln(1 + callCount) / ln(2)
        this.calculatedWeight = Math.log(1.0 + this.callCount) / Math.log(2.0);
    }

    public synchronized int getCallCount() {
        return callCount;
    }

    @Override
    public synchronized double getWeight() {
        return calculatedWeight;
    }

    public synchronized List<InvocationDetail> getCallDetails() {
        return Collections.unmodifiableList(new ArrayList<>(callDetails));
    }
}
