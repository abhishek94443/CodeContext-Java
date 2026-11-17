package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.*;
import com.codecontext.core.model.*;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PageRankNormalizationRegressionTest {
    @Test void normalizedScoresStayInRangeDespiteFloatingPointRounding() {
        for (int seed=0; seed<100; seed++) {
            var random=new Random(seed);
            var graph=new DefaultDirectedWeightedGraph<TypeVertex,DependencyEdge>(DependencyEdge.class);
            var vertices=new ArrayList<TypeVertex>();
            for(int i=0;i<8;i++) {
                var vertex=TypeVertex.of("C"+i,"C"+i,"",TypeKind.CLASS,Path.of("C"+i+".java"));
                vertices.add(vertex); graph.addVertex(vertex);
            }
            for(var from:vertices) for(var to:vertices) if(random.nextDouble()<0.25)
                graph.addEdge(from,to,new DependencyEdge(new InvocationDetail("f","f","f()",new SourceRange(1,1,1,1))));
            assertThatCode(() -> {
                for(var metric:new PageRankEngine().compute(graph,PageRankOptions.defaults()))
                    assertThat(metric.percentileScore()).isBetween(0.0,100.0);
            }).as("graph seed %s",seed).doesNotThrowAnyException();
        }
    }
}
