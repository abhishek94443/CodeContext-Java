package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.InvocationDetail;
import com.codecontext.core.graph.model.TypeVertex;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.util.*;

/**
 * Discovers the shortest directed invocation path between two components
 * using BFS over the dependency graph.
 */
public class PathTraceEngine {

    public record TraceStep(
            int stepNumber,
            TypeVertex source,
            TypeVertex target,
            DependencyEdge edge,
            List<InvocationDetail> callDetails
    ) {}

    public record PathTraceResult(
            boolean pathFound,
            TypeVertex startVertex,
            TypeVertex endVertex,
            int hopCount,
            List<TraceStep> steps,
            String message
    ) {
        public static PathTraceResult notFound(TypeVertex start, TypeVertex end, String message) {
            return new PathTraceResult(false, start, end, 0, List.of(), message);
        }

        public static PathTraceResult found(TypeVertex start, TypeVertex end, List<TraceStep> steps) {
            return new PathTraceResult(true, start, end, steps.size(), steps, "Path found");
        }
    }

    public PathTraceResult tracePath(
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            String sourceName,
            String targetName,
            int maxHops
    ) {
        Objects.requireNonNull(graph, "graph cannot be null");
        Objects.requireNonNull(sourceName, "sourceName cannot be null");
        Objects.requireNonNull(targetName, "targetName cannot be null");

        TypeVertex start = findVertex(graph, sourceName);
        if (start == null) {
            return PathTraceResult.notFound(null, null, "Source class not found in graph: " + sourceName);
        }

        TypeVertex end = findVertex(graph, targetName);
        if (end == null) {
            return PathTraceResult.notFound(start, null, "Target class not found in graph: " + targetName);
        }

        if (start.equals(end)) {
            return PathTraceResult.found(start, end, List.of());
        }

        BFSShortestPath<TypeVertex, DependencyEdge> bfs = new BFSShortestPath<>(graph);
        GraphPath<TypeVertex, DependencyEdge> path = bfs.getPath(start, end);

        if (path == null) {
            return PathTraceResult.notFound(start, end, "No execution path exists from " + start.simpleName() + " to " + end.simpleName());
        }

        if (path.getLength() > maxHops) {
            return PathTraceResult.notFound(start, end, "Path exists but exceeds maximum allowed hops (" + path.getLength() + " > " + maxHops + ")");
        }

        List<TypeVertex> vertexList = path.getVertexList();
        List<DependencyEdge> edgeList = path.getEdgeList();
        List<TraceStep> steps = new ArrayList<>();

        for (int i = 0; i < edgeList.size(); i++) {
            TypeVertex s = vertexList.get(i);
            TypeVertex t = vertexList.get(i + 1);
            DependencyEdge edge = edgeList.get(i);
            steps.add(new TraceStep(i + 1, s, t, edge, edge.getCallDetails()));
        }

        return PathTraceResult.found(start, end, steps);
    }

    private TypeVertex findVertex(DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph, String name) {
        for (TypeVertex v : graph.vertexSet()) {
            if (v.fqcn().equals(name)) return v;
        }
        for (TypeVertex v : graph.vertexSet()) {
            if (v.simpleName().equals(name)) return v;
        }
        return null;
    }
}
