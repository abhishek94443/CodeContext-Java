package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.*;
import com.codecontext.core.graph.export.*;
import com.codecontext.core.model.*;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class GraphCorrectnessRegressionTest {
    private TypeVertex vertex(String name) {
        return TypeVertex.of(name, name, "", TypeKind.CLASS, Path.of(name + ".java"));
    }
    private void edge(DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> g, TypeVertex a, TypeVertex b) {
        g.addEdge(a, b, new DependencyEdge(new InvocationDetail("f", "f", "f()", new SourceRange(1,1,1,1))));
    }
    @Test void selfLoopsAndCyclesExcludeTargetAndPreserveShortestDepth() {
        var g = new DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge>(DependencyEdge.class);
        var a=vertex("A"); var b=vertex("B"); var c=vertex("C");
        List.of(a,b,c).forEach(g::addVertex);
        edge(g,a,a); edge(g,b,a); edge(g,a,b); edge(g,c,b);
        var impact = new BlastRadiusEngine().analyzeImpact("A",g,Map.of(),10);
        assertThat(impact.directCallers()).containsExactly("B");
        assertThat(impact.transitiveCallerDepths()).containsExactlyInAnyOrderEntriesOf(Map.of("B",1,"C",2));
    }
    @Test void oppositeDirectedTrianglesAreDistinct() {
        var g = new DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge>(DependencyEdge.class);
        var a=vertex("A"); var b=vertex("B"); var c=vertex("C");
        List.of(a,b,c).forEach(g::addVertex);
        for(var from:List.of(a,b,c)) for(var to:List.of(a,b,c)) if(!from.equals(to)) edge(g,from,to);
        var cycles = new CycleDetectionEngine().detectCycles(g,new CycleOptions(100,100,10));
        assertThat(cycles.cycles().stream().filter(p -> p.fqcns().size()==4).toList()).hasSize(2);
    }
    @Test void architectureHtmlHasNoRemoteResource(@TempDir Path dir) throws Exception {
        var g = new DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge>(DependencyEdge.class);
        g.addVertex(vertex("A"));
        var output=dir.resolve("architecture.html");
        new HtmlReportExporter().export(new HtmlReportPayload(g,List.of(),new CycleReport(List.of(),0,false),List.of()),output);
        assertThat(Files.readString(output)).doesNotContain("fonts.googleapis.com", "fonts.gstatic.com", "<script src=\"http", "href=\"https://");
    }
}
