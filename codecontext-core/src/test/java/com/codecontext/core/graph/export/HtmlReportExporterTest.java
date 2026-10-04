package com.codecontext.core.graph.export;

import com.codecontext.core.git.GitEvolutionMetric;
import com.codecontext.core.graph.analytics.CyclePath;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.InvocationDetail;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.graph.risk.CompositeRiskScore;
import com.codecontext.core.graph.risk.RiskLevel;
import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.MethodDefinition;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.model.TypeKind;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportExporterTest {

    private HtmlReportExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new HtmlReportExporter();
    }

    @Test
    @DisplayName("FTC-S2.3-004: Export standalone HTML architecture report with package areas and typography")
    void should_export_standalone_html_report_with_packages(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex controller = TypeVertex.of("com.example.controller.OrderController", "OrderController",
                "com.example.controller", TypeKind.CLASS, Path.of("OrderController.java"));
        TypeVertex service = TypeVertex.of("com.example.service.OrderService", "OrderService",
                "com.example.service", TypeKind.CLASS, Path.of("OrderService.java"));

        graph.addVertex(controller);
        graph.addVertex(service);

        DependencyEdge edge = new DependencyEdge(new InvocationDetail("handleOrder", "process", "process()", new SourceRange(15, 5, 15, 25)));
        graph.addEdge(controller, service, edge);

        HtmlReportPayload payload = new HtmlReportPayload(graph, List.of(), new CycleReport(List.of(), 0, false), List.of());

        Path htmlOutput = tempDir.resolve("reports").resolve("architecture.html");
        exporter.export(payload, htmlOutput);

        assertThat(Files.exists(htmlOutput)).isTrue();
        String content = Files.readString(htmlOutput);

        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("CodeContext");
        assertThat(content).contains("com.example.controller");
        assertThat(content).contains("com.example.service");
        assertThat(content).contains("OrderController");
        assertThat(content).contains("OrderService");
        // Typography tokens and fonts per UI specification
        assertThat(content).contains("fonts.googleapis.com");
        assertThat(content).contains("JetBrains Mono");
        assertThat(content).contains("Inter");
    }

    @Test
    @DisplayName("UTC-S3.1-BUG01-001: [BUG-S3.1-01] Canvas container supports scrolling, navigation tabs, and ego-graph stage")
    void should_contain_scrolling_container_and_zoom_controls(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex vertex = TypeVertex.of("com.example.App", "App", "com.example", TypeKind.CLASS, Path.of("App.java"));
        graph.addVertex(vertex);

        HtmlReportPayload payload = new HtmlReportPayload(graph, List.of(), new CycleReport(List.of(), 0, false), List.of());
        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        assertThat(content).contains("id=\"map\"");
        assertThat(content).contains("class=\"stage\"");
        assertThat(content).contains("id=\"graph\"");
        assertThat(content).contains("overflow:auto");
        assertThat(content).contains("data-view=\"map\"");
        assertThat(content).contains("id=\"hint\"");
        assertThat(content).contains("id=\"trail\"");
    }

    @Test
    @DisplayName("UTC-S3.1-BUG03-001: [BUG-S3.1-03] Edge highlighting and blast radius selection on node click")
    void should_support_edge_highlighting_and_dimming(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex a = TypeVertex.of("com.example.A", "A", "com.example", TypeKind.CLASS, Path.of("A.java"));
        TypeVertex b = TypeVertex.of("com.example.B", "B", "com.example", TypeKind.CLASS, Path.of("B.java"));
        graph.addVertex(a);
        graph.addVertex(b);

        DependencyEdge edge = new DependencyEdge(new InvocationDetail("call", "target", "target()", new SourceRange(10, 1, 10, 20)));
        graph.addEdge(a, b, edge);

        HtmlReportPayload payload = new HtmlReportPayload(graph, List.of(), new CycleReport(List.of(), 0, false), List.of());
        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        assertThat(content).contains(".node.selected");
        assertThat(content).contains("state.selected");
        assertThat(content).contains("var(--in)");
        assertThat(content).contains("var(--accent)");
        assertThat(content).contains("var(--hi)");
        assertThat(content).contains("uses this class");
        assertThat(content).contains("used by this class");
    }

    @Test
    @DisplayName("UTC-S3.1-BUG04-001: [BUG-S3.1-04] Text overflow prevention and dynamic truncation")
    void should_prevent_text_overflow_in_sidebar_and_pills(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex longName = TypeVertex.of("com.example.subpkg.VeryLongDescriptiveServiceManagerComponent",
                "VeryLongDescriptiveServiceManagerComponent", "com.example.subpkg", TypeKind.CLASS, Path.of("Comp.java"));
        graph.addVertex(longName);

        HtmlReportPayload payload = new HtmlReportPayload(graph, List.of(), new CycleReport(List.of(), 0, false), List.of());
        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        assertThat(content).contains("word-break");
        assertThat(content).contains("truncate(");
        assertThat(content).contains("overflow");
    }

    @Test
    @DisplayName("UTC-S3.1-FEAT02-001: [FEAT-S3.1-02] Expose architectural cycle chains and interactive circular loop visualizer")
    void should_expose_cycle_chains_and_highlighter(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex a = TypeVertex.of("com.example.A", "A", "com.example", TypeKind.CLASS, Path.of("A.java"));
        TypeVertex b = TypeVertex.of("com.example.B", "B", "com.example", TypeKind.CLASS, Path.of("B.java"));
        graph.addVertex(a);
        graph.addVertex(b);

        CyclePath cycle1 = new CyclePath(List.of("com.example.A", "com.example.B", "com.example.A"));
        CycleReport cycleReport = new CycleReport(List.of(cycle1), 1, false);

        HtmlReportPayload payload = new HtmlReportPayload(graph, List.of(), cycleReport, List.of());
        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        assertThat(content).contains("id=\"loops\"");
        assertThat(content).contains("id=\"loop-list\"");
        assertThat(content).contains("id=\"ring\"");
        assertThat(content).contains("Circular dependencies");
        assertThat(content).contains("renderLoops");
        assertThat(content).contains("openLoop");
    }

    @Test
    @DisplayName("UTC-S3.1-FEAT03-001: [FEAT-S3.1-03] Expose Git evolution, churn, authors, and coupling metrics")
    void should_expose_git_evolution_and_coupling_metrics(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex service = TypeVertex.of("com.example.OrderService", "OrderService", "com.example", TypeKind.CLASS, Path.of("OrderService.java"));
        graph.addVertex(service);

        HotspotMetric hm = new HotspotMetric("com.example.OrderService", Path.of("OrderService.java"), 0.08, 92.0, 5, 2, 1);
        CompositeRiskScore cr = new CompositeRiskScore("com.example.OrderService", Optional.of(Path.of("OrderService.java")), 75.0, RiskLevel.HIGH, 30.0, 20.0, 15.0, 10.0);

        GitEvolutionMetric gm = new GitEvolutionMetric("OrderService.java", 18, 450, 120, 6, 0.45);

        TypeDefinition td = new TypeDefinition(
                "com.example.OrderService", "OrderService", "com.example", TypeKind.CLASS, Path.of("OrderService.java"),
                Optional.empty(), Set.of("ServiceContract"), List.of(), List.of(), Set.of(), new SourceRange(1, 1, 10, 1)
        );

        ConcurrentSymbolTable st = new ConcurrentSymbolTable();
        st.register(td);
        TypeHierarchyIndex hierarchy = new TypeHierarchyIndex(st);
        hierarchy.index(td);

        HtmlReportPayload payload = new HtmlReportPayload(
                graph,
                List.of(hm),
                new CycleReport(List.of(), 0, false),
                List.of(cr),
                Map.of(td.fqcn(), td),
                Map.of("OrderService.java", gm),
                hierarchy
        );

        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        // Verify Git metrics exposed in DATA.rows
        assertThat(content).contains("570"); // 450 + 120
        assertThat(content).contains("6");
        assertThat(content).contains("18");
        // Verify Hotspot & Coupling metrics exposed
        assertThat(content).contains("OrderService");
        assertThat(content).contains("rank");
        assertThat(content).contains("dependents");
        assertThat(content).contains("dependencies");
    }

    @Test
    @DisplayName("UTC-EXT-001: [UI-EXT] Should support lens bar and blast radius (Impact) view components")
    void should_support_lens_bar_and_blast_radius_components(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex a = TypeVertex.of("com.example.OrderController", "OrderController", "com.example", TypeKind.CLASS, Path.of("OrderController.java"));
        TypeVertex b = TypeVertex.of("com.example.OrderService", "OrderService", "com.example", TypeKind.CLASS, Path.of("OrderService.java"));
        graph.addVertex(a);
        graph.addVertex(b);

        DependencyEdge edge = new DependencyEdge(new InvocationDetail("checkout", "process", "process()", new SourceRange(25, 1, 25, 30)));
        graph.addEdge(a, b, edge);

        HtmlReportPayload payload = new HtmlReportPayload(graph, List.of(), new CycleReport(List.of(), 0, false), List.of());
        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        // Lens bar verification
        assertThat(content).contains("lens-bar glass");
        assertThat(content).contains("data-lens=\"connections\"");
        assertThat(content).contains("data-lens=\"impact\"");
        assertThat(content).contains("data-lens=\"calls\"");
        assertThat(content).contains("id=\"lens-summary\"");

        // Impact Lens components
        assertThat(content).contains("id=\"lens-impact\"");
        assertThat(content).contains("class=\"gauge\"");
        assertThat(content).contains("id=\"hops-slider\"");
        assertThat(content).contains("id=\"copy-impact-btn\"");
        assertThat(content).contains("class=\"lanes\"");
        assertThat(content).contains("computeBlastRadius");
        assertThat(content).contains("Biggest blast radius");
        assertThat(content).contains("blast-card");
    }

    @Test
    @DisplayName("UTC-EXT-002: [UI-EXT] Should serialize method declarations and call sites with line numbers for Calls lens")
    void should_serialize_methods_and_call_edges_for_calls_lens(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex a = TypeVertex.of("com.example.Controller", "Controller", "com.example", TypeKind.CLASS, Path.of("Controller.java"));
        TypeVertex b = TypeVertex.of("com.example.Service", "Service", "com.example", TypeKind.CLASS, Path.of("Service.java"));
        graph.addVertex(a);
        graph.addVertex(b);

        DependencyEdge edge = new DependencyEdge(new InvocationDetail("handleRequest", "execute", "execute()", new SourceRange(142, 1, 142, 20)));
        graph.addEdge(a, b, edge);

        MethodDefinition md1 = new MethodDefinition("handleRequest", "handleRequest()", "void", List.of(), List.of(), Set.of("public"), false, new SourceRange(140, 1, 150, 1));
        TypeDefinition tdA = new TypeDefinition(
                "com.example.Controller", "Controller", "com.example", TypeKind.CLASS, Path.of("Controller.java"),
                Optional.empty(), Set.of(), List.of(md1), List.of(), Set.of(), new SourceRange(1, 1, 160, 1)
        );

        HtmlReportPayload payload = new HtmlReportPayload(
                graph,
                List.of(),
                new CycleReport(List.of(), 0, false),
                List.of(),
                Map.of(tdA.fqcn(), tdA)
        );

        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        // Verify DATA.methods and DATA.calls presence
        assertThat(content).contains("methods:");
        assertThat(content).contains("handleRequest");
        assertThat(content).contains("calls:");
        assertThat(content).contains("Controller.handleRequest>Service.execute:L142");

        // Calls lens UI elements
        assertThat(content).contains("id=\"lens-calls\"");
        assertThat(content).contains("id=\"method-strip\"");
        assertThat(content).contains("id=\"method-graph\"");
        assertThat(content).contains("id=\"trace-panel\"");
        assertThat(content).contains("findMethodPath");
    }

    @Test
    @DisplayName("UTC-EXT-003: [UI-EXT] Should support interactive zoom dock, instability ratio, and hierarchy implementors")
    void should_support_zoom_dock_and_hierarchy_implementors(@TempDir Path tempDir) throws Exception {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex iface = TypeVertex.of("com.example.Contract", "Contract", "com.example", TypeKind.INTERFACE, Path.of("Contract.java"));
        TypeVertex impl = TypeVertex.of("com.example.ContractImpl", "ContractImpl", "com.example", TypeKind.CLASS, Path.of("ContractImpl.java"));
        graph.addVertex(iface);
        graph.addVertex(impl);

        TypeDefinition tdIface = new TypeDefinition(
                "com.example.Contract", "Contract", "com.example", TypeKind.INTERFACE, Path.of("Contract.java"),
                Optional.empty(), Set.of(), List.of(), List.of(), Set.of(), new SourceRange(1, 1, 10, 1)
        );
        TypeDefinition tdImpl = new TypeDefinition(
                "com.example.ContractImpl", "ContractImpl", "com.example", TypeKind.CLASS, Path.of("ContractImpl.java"),
                Optional.empty(), Set.of("Contract"), List.of(), List.of(), Set.of(), new SourceRange(1, 1, 20, 1)
        );

        ConcurrentSymbolTable st = new ConcurrentSymbolTable();
        st.register(tdIface);
        st.register(tdImpl);
        TypeHierarchyIndex hierarchy = new TypeHierarchyIndex(st);
        hierarchy.index(tdIface);
        hierarchy.index(tdImpl);

        HtmlReportPayload payload = new HtmlReportPayload(
                graph,
                List.of(),
                new CycleReport(List.of(), 0, false),
                List.of(),
                Map.of(tdIface.fqcn(), tdIface, tdImpl.fqcn(), tdImpl),
                Map.of(),
                hierarchy
        );

        Path htmlOutput = tempDir.resolve("graph.html");
        exporter.export(payload, htmlOutput);

        String content = Files.readString(htmlOutput);
        // Zoom controls
        assertThat(content).contains("id=\"zoom-dock\"");
        assertThat(content).contains("id=\"zoom-in\"");
        assertThat(content).contains("id=\"zoom-out\"");
        assertThat(content).contains("id=\"zoom-reset\"");

        // Hierarchy Implementations & Instability
        assertThat(content).contains("IMPLEMENTATIONS");
        assertThat(content).contains("ContractImpl");
        assertThat(content).contains("Instability ratio");

        // Markdown checklist export and noise filtering
        assertThat(content).contains("id=\"copy-md-btn\"");
        assertThat(content).contains("id=\"toggle-boilerplate-btn\"");
    }
}
