package com.codecontext.core.graph.export;

import com.codecontext.core.git.GitEvolutionMetric;
import com.codecontext.core.graph.analytics.CyclePath;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.InvocationDetail;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.graph.risk.CompositeRiskScore;
import com.codecontext.core.graph.risk.RiskLevel;
import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.model.MethodDefinition;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.model.TypeKind;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates an enterprise-grade, zero-dependency, self-contained interactive HTML architectural report.
 * Fully compliant with UI-SPEC.md, ui-extension.md, and approved UI Completion Plan.
 */
public class HtmlReportExporter {

    public void export(HtmlReportPayload payload, Path outputPath) throws IOException {
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        String htmlContent = generateHtml(payload);
        Files.writeString(outputPath, htmlContent, StandardCharsets.UTF_8);
    }

    String generateHtml(HtmlReportPayload payload) {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = payload.graph();

        Map<String, CompositeRiskScore> riskMap = payload.riskScores().stream()
                .collect(Collectors.toMap(CompositeRiskScore::fqcn, r -> r, (a, b) -> a));

        Map<String, HotspotMetric> hotspotMap = payload.hotspots().stream()
                .collect(Collectors.toMap(HotspotMetric::fqcn, h -> h, (a, b) -> a));

        // 1. Resolve Unique Simple IDs for UI
        Map<String, String> fqcnToId = new HashMap<>();
        Map<String, Integer> simpleNameCounts = new HashMap<>();
        for (TypeVertex v : graph.vertexSet()) {
            if (v.isBoundaryNode()) continue;
            simpleNameCounts.merge(v.simpleName(), 1, Integer::sum);
        }
        for (TypeVertex v : graph.vertexSet()) {
            if (v.isBoundaryNode()) continue;
            if (simpleNameCounts.getOrDefault(v.simpleName(), 0) > 1) {
                fqcnToId.put(v.fqcn(), v.fqcn());
            } else {
                fqcnToId.put(v.fqcn(), v.simpleName());
            }
        }

        // 2. Build Architectural Areas (Packages)
        Map<String, String> pkgToKey = new HashMap<>();
        StringBuilder areasJson = new StringBuilder("{\n");
        int pkgIdx = 0;
        for (TypeVertex v : graph.vertexSet()) {
            if (v.isBoundaryNode()) continue;
            String pkg = v.packageName() != null && !v.packageName().isBlank() ? v.packageName() : "default";
            if (!pkgToKey.containsKey(pkg)) {
                String key = "p" + (pkgIdx++);
                pkgToKey.put(pkg, key);
                String shortTitle = pkg.contains(".") ? pkg.substring(pkg.lastIndexOf('.') + 1) : pkg;
                if (pkgIdx > 1) areasJson.append(",\n");
                areasJson.append("  ").append(escapeJson(key)).append(": [")
                         .append(escapeJson(pkg)).append(", ")
                         .append(escapeJson(shortTitle)).append(", ")
                         .append(escapeJson("[Package] " + pkg)).append("]");
            }
        }
        areasJson.append("\n}");

        // 3. Build Descriptions, Interfaces Set, and Compact Aliases
        StringBuilder descJson = new StringBuilder("{\n");
        StringBuilder ifacesJson = new StringBuilder("[");
        StringBuilder aliasesJson = new StringBuilder("{\n");

        int aliasIdx = 0;
        Map<String, String> fqcnToAlias = new HashMap<>();
        boolean firstDesc = true;
        boolean firstIface = true;
        boolean firstAlias = true;

        for (TypeVertex v : graph.vertexSet()) {
            if (v.isBoundaryNode()) continue;
            String id = fqcnToId.get(v.fqcn());
            String alias = "c" + (aliasIdx++);
            fqcnToAlias.put(v.fqcn(), alias);

            if (!firstAlias) aliasesJson.append(",\n");
            firstAlias = false;
            aliasesJson.append("  ").append(escapeJson(alias)).append(": ").append(escapeJson(id));

            TypeDefinition td = payload.typeDefinitions().get(v.fqcn());
            if (v.kind() == TypeKind.INTERFACE) {
                if (!firstIface) ifacesJson.append(", ");
                firstIface = false;
                ifacesJson.append(escapeJson(id));
            }

            String desc = "";
            if (td != null && !td.interfaces().isEmpty()) {
                desc = "Implements " + String.join(", ", td.interfaces()) + ".";
            } else if (td != null && td.superclass().isPresent()) {
                desc = "Extends " + td.superclass().get() + ".";
            } else {
                desc = "Part of the " + (v.packageName().isBlank() ? "default" : v.packageName()) + " package.";
            }

            if (!firstDesc) descJson.append(",\n");
            firstDesc = false;
            descJson.append("  ").append(escapeJson(id)).append(": ").append(escapeJson(desc));
        }
        descJson.append("\n}");
        ifacesJson.append("]");
        aliasesJson.append("\n}");

        // 4. Build Downstream Hierarchy (Implementations / Subclasses)
        StringBuilder implsJson = new StringBuilder("{\n");
        TypeHierarchyIndex hierarchy = payload.hierarchyIndex();
        boolean firstImpl = true;
        for (TypeVertex v : graph.vertexSet()) {
            if (v.isBoundaryNode()) continue;
            String id = fqcnToId.get(v.fqcn());
            Set<String> subFqcns = new LinkedHashSet<>();
            if (hierarchy != null) {
                subFqcns.addAll(hierarchy.findDirectImplementors(v.fqcn()));
                subFqcns.addAll(hierarchy.findDirectSubclasses(v.fqcn()));
            }
            if (!subFqcns.isEmpty()) {
                if (!firstImpl) implsJson.append(",\n");
                firstImpl = false;
                implsJson.append("  ").append(escapeJson(id)).append(": [");
                boolean firstSub = true;
                for (String subFqcn : subFqcns) {
                    String subId = fqcnToId.get(subFqcn);
                    if (subId != null) {
                        if (!firstSub) implsJson.append(", ");
                        firstSub = false;
                        implsJson.append(escapeJson(subId));
                    }
                }
                implsJson.append("]");
            }
        }
        implsJson.append("\n}");

        // 5. Build Rows (DATA.rows)
        StringBuilder rowsJson = new StringBuilder("[\n");
        boolean firstRow = true;
        for (TypeVertex v : graph.vertexSet()) {
            if (v.isBoundaryNode()) continue;
            if (!firstRow) rowsJson.append(",\n");
            firstRow = false;

            String fqcn = v.fqcn();
            String id = fqcnToId.get(fqcn);
            String pkg = v.packageName() != null && !v.packageName().isBlank() ? v.packageName() : "default";
            String areaKey = pkgToKey.getOrDefault(pkg, "p0");

            CompositeRiskScore risk = riskMap.get(fqcn);
            double riskVal = risk != null ? risk.overallScore() : 0.0;
            double prF = risk != null ? risk.pageRankFactor() : 0.0;
            double chF = risk != null ? risk.churnFactor() : 0.0;

            HotspotMetric hm = hotspotMap.get(fqcn);
            int inDeg = hm != null ? hm.inDegree() : graph.inDegreeOf(v);
            int outDeg = hm != null ? hm.outDegree() : graph.outDegreeOf(v);
            int rank = hm != null ? hm.rank() : 1;

            TypeDefinition td = payload.typeDefinitions().get(fqcn);
            GitEvolutionMetric gm = findGitMetric(td, payload.gitMetrics());
            int churn = gm != null ? (gm.linesAdded() + gm.linesDeleted()) : 0;
            int commits = gm != null ? gm.commitCount() : 0;
            int authors = gm != null ? gm.uniqueAuthors() : 0;

            rowsJson.append("    [").append(escapeJson(id)).append(", ")
                    .append(escapeJson(areaKey)).append(", ")
                    .append(String.format(Locale.ROOT, "%.1f", riskVal)).append(", ")
                    .append(rank).append(", ")
                    .append(inDeg).append(", ")
                    .append(outDeg).append(", ")
                    .append(churn).append(", ")
                    .append(commits).append(", ")
                    .append(authors).append(", ")
                    .append(String.format(Locale.ROOT, "%.1f", prF)).append(", ")
                    .append(String.format(Locale.ROOT, "%.1f", chF)).append("]");
        }
        rowsJson.append("\n  ]");

        // 6. Build Edges (DATA.edges)
        StringBuilder edgesStr = new StringBuilder();
        for (DependencyEdge edge : graph.edgeSet()) {
            TypeVertex src = graph.getEdgeSource(edge);
            TypeVertex tgt = graph.getEdgeTarget(edge);
            if (src == null || tgt == null || src.isBoundaryNode() || tgt.isBoundaryNode()) continue;
            String sAlias = fqcnToAlias.get(src.fqcn());
            String tAlias = fqcnToAlias.get(tgt.fqcn());
            if (sAlias == null || tAlias == null) continue;

            if (edgesStr.length() > 0) edgesStr.append(" ");
            edgesStr.append(sAlias).append(">").append(tAlias).append(":").append(edge.getCallCount());
        }

        // 7. Build Loops (DATA.loops)
        StringBuilder loopsJson = new StringBuilder("[");
        if (payload.cycleReport() != null && payload.cycleReport().cycles() != null) {
            boolean firstLoop = true;
            for (CyclePath cp : payload.cycleReport().cycles()) {
                if (cp.fqcns() == null || cp.fqcns().isEmpty()) continue;
                int limit = cp.fqcns().size();
                if (limit > 1 && cp.fqcns().get(0).equals(cp.fqcns().get(limit - 1))) {
                    limit--; // Strip closing duplicate because JS loops.forEach(l => l.push(l[0])) appends it
                }
                StringBuilder loopTokens = new StringBuilder();
                for (int i = 0; i < limit; i++) {
                    String fqcn = cp.fqcns().get(i);
                    String alias = fqcnToAlias.get(fqcn);
                    if (alias != null) {
                        if (loopTokens.length() > 0) loopTokens.append(" ");
                        loopTokens.append(alias);
                    }
                }
                if (loopTokens.length() > 0) {
                    if (!firstLoop) loopsJson.append(", ");
                    firstLoop = false;
                    loopsJson.append(escapeJson(loopTokens.toString()));
                }
            }
        }
        loopsJson.append("]");

        // 8. Build Methods & Calls with Parameter Signature Disambiguation
        Map<String, Set<String>> classToMethods = new LinkedHashMap<>();
        for (TypeVertex v : graph.vertexSet()) {
            if (v.isBoundaryNode()) continue;
            String id = fqcnToId.get(v.fqcn());
            TypeDefinition td = payload.typeDefinitions().get(v.fqcn());
            Set<String> methodNames = new LinkedHashSet<>();
            if (td != null && td.methods() != null) {
                // Count method name frequencies to disambiguate overloads
                Map<String, Integer> nameCounts = new HashMap<>();
                for (MethodDefinition md : td.methods()) {
                    if (md.name() != null && !md.name().isBlank()) {
                        nameCounts.merge(md.name(), 1, Integer::sum);
                    }
                }
                for (MethodDefinition md : td.methods()) {
                    if (md.name() != null && !md.name().isBlank()) {
                        if (nameCounts.getOrDefault(md.name(), 0) > 1 && md.parameterTypes() != null && !md.parameterTypes().isEmpty()) {
                            // Short parameter signature for overloads
                            String paramShort = md.parameterTypes().stream()
                                    .map(p -> p.contains(".") ? p.substring(p.lastIndexOf('.') + 1) : p)
                                    .collect(Collectors.joining(","));
                            methodNames.add(md.name() + "(" + paramShort + ")");
                        } else {
                            methodNames.add(md.name());
                        }
                    }
                }
            }
            classToMethods.put(id, methodNames);
        }

        StringBuilder callsStr = new StringBuilder();
        for (DependencyEdge edge : graph.edgeSet()) {
            TypeVertex src = graph.getEdgeSource(edge);
            TypeVertex tgt = graph.getEdgeTarget(edge);
            if (src == null || tgt == null || src.isBoundaryNode() || tgt.isBoundaryNode()) continue;
            String sId = fqcnToId.get(src.fqcn());
            String tId = fqcnToId.get(tgt.fqcn());
            if (sId == null || tId == null) continue;

            for (InvocationDetail inv : edge.getCallDetails()) {
                String cMethod = inv.callerMethodName();
                String tMethod = inv.targetMethodName();
                if (cMethod == null || cMethod.isBlank() || "<clinit>".equals(cMethod)) cMethod = "(init)";
                if (tMethod == null || tMethod.isBlank()) tMethod = "(call)";

                classToMethods.computeIfAbsent(sId, k -> new LinkedHashSet<>()).add(cMethod);
                classToMethods.computeIfAbsent(tId, k -> new LinkedHashSet<>()).add(tMethod);

                int line = (inv.range() != null) ? inv.range().startLine() : 0;
                if (callsStr.length() > 0) callsStr.append(" ");
                callsStr.append(sId).append(".").append(cMethod)
                        .append(">")
                        .append(tId).append(".").append(tMethod)
                        .append(":L").append(line);
            }
        }

        StringBuilder methodsJson = new StringBuilder("{\n");
        boolean firstClassMethod = true;
        for (Map.Entry<String, Set<String>> entry : classToMethods.entrySet()) {
            if (!firstClassMethod) methodsJson.append(",\n");
            firstClassMethod = false;
            methodsJson.append("  ").append(escapeJson(entry.getKey())).append(": [");
            boolean firstM = true;
            for (String m : entry.getValue()) {
                if (!firstM) methodsJson.append(", ");
                firstM = false;
                methodsJson.append(escapeJson(m));
            }
            methodsJson.append("]");
        }
        methodsJson.append("\n}");

        // 9. Summary metrics
        long totalNodes = graph.vertexSet().stream().filter(v -> !v.isBoundaryNode()).count();
        int totalEdges = graph.edgeSet().size();
        long criticalCount = riskMap.values().stream().filter(r -> r.level() == RiskLevel.CRITICAL).count();
        int loopsCount = payload.cycleReport().cycles().size();

        String summaryJson = String.format(Locale.ROOT,
                "{ classes: %d, connections: %d, critical: %d, loops: %d }",
                totalNodes, totalEdges, criticalCount, loopsCount);

        return HTML_TEMPLATE
                .replace("{{SUMMARY_LOOPS}}", String.valueOf(loopsCount))
                .replace("{{AREAS}}", areasJson.toString())
                .replace("{{DESCRIPTIONS}}", descJson.toString())
                .replace("{{INTERFACES}}", ifacesJson.toString())
                .replace("{{IMPLEMENTATIONS}}", implsJson.toString())
                .replace("{{SUMMARY}}", summaryJson)
                .replace("{{ROWS}}", rowsJson.toString())
                .replace("{{ALIASES}}", aliasesJson.toString())
                .replace("{{EDGES}}", edgesStr.toString())
                .replace("{{LOOPS}}", loopsJson.toString())
                .replace("{{METHODS}}", methodsJson.toString())
                .replace("{{CALLS}}", callsStr.toString());
    }

    private GitEvolutionMetric findGitMetric(TypeDefinition td, Map<String, GitEvolutionMetric> gitMetrics) {
        if (td == null || gitMetrics == null || gitMetrics.isEmpty()) return null;
        String pathStr = td.normalizedPathString();
        for (Map.Entry<String, GitEvolutionMetric> entry : gitMetrics.entrySet()) {
            if (pathStr.endsWith(entry.getKey()) || entry.getKey().endsWith(pathStr)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32 || c > 126) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

        private static final String HTML_PART_1 = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>CodeContext \u00b7 Architecture Report</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
<style>
/* ============ 1. TOKENS (see UI-SPEC.md \u00a72) ============ */
:root {
  --bg:#0a0d14; --dot:rgba(255,255,255,.05); --surface:rgba(20,26,38,.78); --card:#151b29;
  --line:rgba(255,255,255,.09); --text:#e8ecf4; --muted:#8b95a9;
  --accent:#7c9cff; --in:#c792ff; --hi:#ff6b57; --med:#f5b84b; --low:#4cc3a8;
}
@media (prefers-color-scheme:light) {
  :root:not([data-theme="dark"]) { --bg:#f4f6fb; --dot:rgba(20,26,41,.07); --surface:rgba(255,255,255,.85); --card:#fff; --line:rgba(20,26,41,.11); --text:#141a29; --muted:#5b6578; --accent:#4a6cf7; --in:#8b4fe0; }
}
:root[data-theme="light"] { --bg:#f4f6fb; --dot:rgba(20,26,41,.07); --surface:rgba(255,255,255,.85); --card:#fff; --line:rgba(20,26,41,.11); --text:#141a29; --muted:#5b6578; --accent:#4a6cf7; --in:#8b4fe0; }

/* ============ 2. BASE ============ */
* { box-sizing:border-box; margin:0; padding:0; }
:root { padding-top:env(safe-area-inset-top,0px); padding-bottom:env(safe-area-inset-bottom,0px); }
html { scroll-padding-top:env(safe-area-inset-top,0px); }
html, body { height:100%; }
body { font-family:Inter,system-ui,-apple-system,"Segoe UI",sans-serif; background:var(--bg); color:var(--text); display:flex; flex-direction:column; overflow:hidden; -webkit-font-smoothing:antialiased; }
button { font:inherit; color:inherit; cursor:pointer; }
.mono { font-family:"JetBrains Mono",ui-monospace,monospace; }
.glass { background:var(--surface); backdrop-filter:blur(18px) saturate(1.4); -webkit-backdrop-filter:blur(18px) saturate(1.4); }
.hidden { display:none !important; }
.view { flex:1; min-height:0; display:flex; }

/* ============ 3. HEADER ============ */
.top { display:flex; align-items:center; gap:18px; padding:10px 20px; border-bottom:1px solid var(--line); z-index:6; flex-wrap:wrap; }
.brand { display:flex; gap:10px; align-items:center; font-weight:600; font-size:14px; }
.logo { width:30px; height:30px; border-radius:9px; background:linear-gradient(135deg,var(--accent),var(--in)); display:grid; place-items:center; color:#fff; font-size:13px; font-weight:700; }
.tabs { display:flex; gap:4px; background:var(--card); padding:3px; border-radius:11px; border:1px solid var(--line); }
.tabs button { border:0; background:none; padding:6px 14px; border-radius:8px; font-size:13px; font-weight:500; color:var(--muted); }
.tabs button.on { background:var(--accent); color:#fff; }
.tabs em { font-style:normal; font-size:11px; margin-left:5px; background:var(--hi); color:#fff; border-radius:99px; padding:1px 6px; }
.search { position:relative; flex:1; max-width:420px; margin-left:auto; }
.search input { width:100%; padding:8px 36px 8px 32px; border-radius:10px; border:1px solid var(--line); background:var(--card); color:var(--text); font:inherit; font-size:13px; outline:none; }
.search input:focus { border-color:var(--accent); box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 22%,transparent); }
.search i { position:absolute; left:11px; top:7px; font-style:normal; color:var(--muted); }
.search kbd { position:absolute; right:8px; top:8px; font:500 10px "JetBrains Mono",monospace; color:var(--muted); border:1px solid var(--line); border-radius:5px; padding:1px 5px; }
#results { position:absolute; left:0; right:0; top:40px; background:var(--card); border:1px solid var(--line); border-radius:10px; display:none; overflow:hidden; box-shadow:0 12px 30px rgba(0,0,0,.35); z-index:10; }
#results button { display:flex; justify-content:space-between; width:100%; padding:9px 12px; border:0; background:none; font-size:13px; text-align:left; }
#results button:hover { background:color-mix(in srgb,var(--accent) 16%,transparent); }
#results span { color:var(--muted); font-size:11px; }
.icon { width:34px; height:34px; border-radius:10px; border:1px solid var(--line); background:var(--card); }

/* ============ 4. OVERVIEW ============ */
#home { display:block; overflow-y:auto; padding:32px max(20px,calc((100% - 1080px)/2)) 60px; }
.eyebrow { font-size:12px; color:var(--accent); font-weight:600; letter-spacing:.08em; text-transform:uppercase; }
h1 { font-size:clamp(26px,4vw,38px); letter-spacing:-.03em; margin:6px 0 10px; }
h2 { font-size:15px; margin:34px 0 12px; letter-spacing:-.01em; }
.lead { color:var(--muted); font-size:15px; line-height:1.6; max-width:70ch; }
.lead b { color:var(--text); }
.kpis { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:12px; margin-top:24px; }
.kpi { background:var(--card); border:1px solid var(--line); border-radius:16px; padding:16px; text-align:left; transition:transform .15s,border-color .15s; }
button.kpi:hover { transform:translateY(-2px); border-color:var(--hi); }
.kpi h3 { font-size:11px; text-transform:uppercase; letter-spacing:.08em; color:var(--muted); font-weight:600; }
.kpi b { display:block; font-size:30px; letter-spacing:-.03em; }
.kpi small { color:var(--muted); font-size:12px; line-height:1.4; display:block; margin-top:2px; }
.row { display:flex; align-items:center; gap:14px; width:100%; text-align:left; padding:12px 14px; margin-bottom:8px; border-radius:14px; border:1px solid var(--line); background:var(--card); transition:transform .15s,border-color .15s; }
.row:hover { border-color:var(--accent); transform:translateX(3px); }
.row .n { flex:1; min-width:0; }
.row b { font-size:14px; font-weight:600; }
.row p { font-size:12.5px; color:var(--muted); margin-top:2px; }
.meter { width:90px; height:6px; border-radius:99px; background:var(--line); overflow:hidden; flex:none; }
.meter i { display:block; height:100%; border-radius:99px; }
.score { font:600 13px "JetBrains Mono",monospace; width:38px; text-align:right; flex:none; }
.tag { display:inline-block; font-size:10.5px; font-weight:600; padding:2px 8px; border-radius:99px; border:1px solid currentColor; margin-right:5px; }
.areas { display:grid; grid-template-columns:repeat(auto-fill,minmax(310px,1fr)); gap:12px; }
.area { background:var(--card); border:1px solid var(--line); border-radius:16px; padding:16px; }
.area h3 { font-size:14px; }
.area p { font-size:12px; color:var(--muted); margin:3px 0 10px; }
.chip { display:inline-flex; align-items:center; gap:6px; margin:0 6px 6px 0; padding:5px 10px; border-radius:99px; border:1px solid var(--line); background:none; font-size:12px; }
.chip:hover { border-color:var(--accent); }
.dot { width:7px; height:7px; border-radius:50%; flex:none; }

/* ============ 5. EXPLORE: CARD + CANVAS ============ */
aside { width:350px; flex:none; overflow:auto; padding:20px; border-right:1px solid var(--line); }
aside h2 { margin:0 0 6px; font-size:20px; letter-spacing:-.02em; word-break:break-word; }
.fq { font-size:11px; color:var(--muted); word-break:break-all; margin:6px 0 10px; }
.desc { font-size:14px; line-height:1.55; margin:10px 0 14px; }
.stats { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin:14px 0; }
.stats div { padding:10px 12px; border-radius:12px; background:var(--card); border:1px solid var(--line); }
.stats b { display:block; font-size:20px; }
.stats small { font-size:11px; color:var(--muted); }
.advice { background:color-mix(in srgb,var(--accent) 12%,transparent); border:1px solid color-mix(in srgb,var(--accent) 40%,transparent); border-radius:12px; padding:12px; font-size:13px; line-height:1.5; }
.advice b { display:block; font-size:11px; text-transform:uppercase; letter-spacing:.08em; color:var(--accent); margin-bottom:3px; }
.facts { list-style:none; margin:12px 0; }
.facts li { display:flex; gap:10px; font-size:13px; line-height:1.45; padding:8px 0; border-bottom:1px solid var(--line); }
.facts .dot { margin-top:6px; }
.bars { margin:14px 0; }
.bars div { margin-bottom:9px; font-size:12px; color:var(--muted); }
.bars span { display:flex; justify-content:space-between; margin-bottom:4px; }
.bars .meter { width:100%; }
.btn { display:inline-block; margin:8px 8px 0 0; padding:7px 12px; border-radius:9px; border:1px solid var(--line); background:var(--card); font-size:12.5px; }
.btn:hover { border-color:var(--accent); }
.stage { flex:1; position:relative; overflow:hidden; min-width:0; background-image:radial-gradient(var(--dot) 1px,transparent 1px); background-size:22px 22px; display:flex; flex-direction:column; user-select:none; }
.stage.grabbing { cursor:grabbing !important; }
.stage.grab { cursor:grab; }
.hint { position:sticky; top:12px; margin:12px auto 0; width:max-content; max-width:calc(100% - 24px); display:flex; gap:12px; align-items:center; padding:8px 14px; border-radius:12px; border:1px solid var(--line); font-size:12.5px; color:var(--muted); z-index:3; }
.hint button { border:0; background:none; color:var(--muted); font-size:16px; }
.trail { display:flex; gap:8px; align-items:center; padding:12px 16px 0; font-size:12.5px; color:var(--muted); flex-wrap:wrap; z-index:2; }
#legend { position:sticky; bottom:12px; left:12px; width:max-content; margin:0 0 12px 12px; display:flex; gap:16px; padding:8px 14px; border-radius:12px; border:1px solid var(--line); font-size:12px; color:var(--muted); z-index:3; }
#legend i { display:inline-block; width:18px; height:3px; border-radius:2px; margin-right:6px; vertical-align:middle; }
svg { display:block; width:100%; height:auto; min-width:760px; margin:0 auto; transform-origin:500px 200px; transition:transform .05s ease-out; }
.node { cursor:pointer; outline:none; }
.node rect:first-child { fill:var(--card); stroke:var(--line); transition:stroke .15s; }
.node:hover rect:first-child, .node:focus rect:first-child { stroke:var(--accent); }
.node.selected rect:first-child { stroke:var(--accent); stroke-width:2.5; }
.node.more rect:first-child { stroke-dasharray:4 4; }
.node text { fill:var(--text); font:500 13px Inter,sans-serif; }
.node .sub { fill:var(--muted); font-size:10.5px; font-weight:400; }
.node .count { fill:var(--muted); font:500 11px "JetBrains Mono",monospace; text-anchor:end; }
.col-title { fill:var(--muted); font:600 11px Inter,sans-serif; letter-spacing:.08em; text-anchor:middle; text-transform:uppercase; }
.empty { fill:var(--muted); font:13px Inter,sans-serif; text-anchor:middle; }

/* Floating Zoom Dock */
.zoom-dock { position:absolute; bottom:16px; right:16px; display:flex; gap:4px; padding:4px; border-radius:12px; border:1px solid var(--line); z-index:5; box-shadow:0 6px 18px rgba(0,0,0,.25); }
.zoom-dock button { width:32px; height:32px; display:grid; place-items:center; border:0; background:none; border-radius:8px; font-size:14px; font-weight:600; color:var(--muted); transition:background .15s,color .15s; }
.zoom-dock button:hover { background:color-mix(in srgb,var(--accent) 18%,transparent); color:var(--text); }

/* ============ 6. EXTENSIONS: LENS BAR & LENSES ============ */
.lens-bar { display:flex; align-items:center; gap:16px; padding:10px 16px; border-bottom:1px solid var(--line); flex-wrap:wrap; z-index:4; }
.pills { display:inline-flex; gap:4px; background:var(--card); padding:3px; border-radius:99px; border:1px solid var(--line); }
.pill { border:0; background:none; padding:7px 14px; border-radius:99px; font-size:12.5px; font-weight:500; color:var(--muted); transition:background .15s,color .15s; }
.pill.on { background:var(--accent); color:#fff; font-weight:600; }
.lens-summary { font-size:12.5px; color:var(--muted); }

/* Blast Card & Implementations in Sidebar */
.blast-card { margin:14px 0; padding:12px; border-radius:12px; background:color-mix(in srgb,var(--accent) 8%,var(--card)); border:1px solid color-mix(in srgb,var(--accent) 30%,transparent); cursor:pointer; transition:border-color .15s; }
.blast-card:hover { border-color:var(--accent); }
.blast-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; font-size:12.5px; }
.blast-card p { font-size:12.5px; line-height:1.45; color:var(--muted); margin:0; }
.blast-card b { color:var(--text); }

.impl-box { margin:14px 0; padding:12px; border-radius:12px; background:var(--card); border:1px solid var(--line); }
.impl-head { font-size:11px; text-transform:uppercase; letter-spacing:.08em; color:var(--muted); font-weight:600; margin-bottom:8px; }

/* Impact Lens View */
#lens-impact { padding:20px 24px 60px; max-width:1200px; width:100%; margin:0 auto; overflow-y:auto; }
.impact-head { display:flex; gap:20px; align-items:center; margin-bottom:20px; flex-wrap:wrap; }
.gauge { width:92px; height:92px; border-radius:50%; background:conic-gradient(var(--c, var(--low)) calc(var(--pct, 0) * 1%), var(--line) 0); display:grid; place-items:center; flex:none; }
.gauge-inner { width:72px; height:72px; border-radius:50%; background:var(--card); display:grid; place-items:center; font:600 18px "JetBrains Mono",monospace; color:var(--text); }
.impact-intro h3 { font-size:20px; letter-spacing:-.02em; margin-bottom:4px; }
.impact-intro p { font-size:13.5px; color:var(--muted); line-height:1.5; }
.controls { display:flex; gap:16px; align-items:center; margin:16px 0; flex-wrap:wrap; }
.seg { display:inline-flex; background:var(--card); border:1px solid var(--line); border-radius:9px; padding:2px; }
.seg button { border:0; background:none; padding:6px 12px; border-radius:7px; font-size:12px; color:var(--muted); }
.seg button.on { background:var(--surface); color:var(--text); font-weight:600; box-shadow:0 1px 3px rgba(0,0,0,.2); }
.slider-wrap { display:flex; align-items:center; gap:8px; font-size:12.5px; color:var(--muted); }
.slider-wrap input[type=range] { accent-color:var(--accent); cursor:pointer; }
.filters-wrap { display:flex; gap:6px; margin:12px 0 8px; align-items:center; font-size:12px; }
.filter-btn { border:1px solid var(--line); background:var(--card); padding:4px 10px; border-radius:8px; font-size:12px; color:var(--muted); }
.filter-btn.on { background:var(--accent); color:#fff; border-color:var(--accent); font-weight:500; }
.area-summary { display:flex; gap:8px; flex-wrap:wrap; margin:12px 0 16px; }
.lanes { display:flex; gap:16px; overflow-x:auto; padding-bottom:16px; align-items:flex-start; }
.lane { min-width:260px; max-width:300px; flex:none; background:var(--card); border:1px solid var(--line); border-radius:16px; overflow:hidden; animation:rise .45s ease backwards; }
.lane-bar { height:4px; width:100%; }
.lane-title { padding:12px 14px 8px; border-bottom:1px solid var(--line); font-size:13px; font-weight:600; display:flex; justify-content:space-between; }
.lane-content { padding:10px 14px; max-height:440px; overflow-y:auto; }
.lane-group { margin-bottom:12px; }
.lane-group-title { font-size:10.5px; text-transform:uppercase; letter-spacing:.08em; color:var(--muted); font-weight:600; margin-bottom:6px; display:flex; justify-content:space-between; }
.lane-content details { margin-top:4px; }
.lane-content summary { font-size:11.5px; color:var(--accent); cursor:pointer; padding:3px 0; outline:none; }
@keyframes rise { from { opacity:0; transform:translateY(12px); } to { opacity:1; transform:translateY(0); } }

/* Calls Lens View */
#lens-calls { display:flex; flex-direction:column; flex:1; min-height:0; position:relative; overflow:hidden; }
.method-strip { display:flex; gap:6px; padding:10px 16px; border-bottom:1px solid var(--line); overflow-x:auto; align-items:center; flex-wrap:wrap; background:color-mix(in srgb,var(--card) 60%,transparent); z-index:2; }
.method-chip { display:inline-flex; align-items:center; gap:6px; padding:4px 10px; border-radius:99px; border:1px solid var(--line); background:var(--card); font-size:12px; color:var(--text); }
.method-chip.on { border-color:var(--accent); background:color-mix(in srgb,var(--accent) 15%,var(--card)); font-weight:600; }
.method-chip:hover { border-color:var(--accent); }
.trace-panel { padding:12px 16px; border-top:1px solid var(--line); background:var(--card); display:flex; flex-direction:column; gap:8px; font-size:12.5px; z-index:2; }
.trace-controls { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
.narrative { margin-top:6px; padding:10px 14px; border-radius:10px; background:var(--bg); border:1px solid var(--line); font-family:"JetBrains Mono",monospace; font-size:12px; line-height:1.6; }

/* ============ 7. LOOPS ============ */
.loop-card { display:block; width:100%; text-align:left; padding:12px; margin-bottom:8px; border-radius:12px; border:1px solid var(--line); background:var(--card); }
.loop-card:hover, .loop-card.on { border-color:var(--hi); }
.loop-card.on { background:color-mix(in srgb,var(--hi) 10%,var(--card)); }
.loop-card b { font-size:13px; }
.loop-card p { font-size:12px; color:var(--muted); margin-top:3px; line-height:1.4; }
.explain { max-width:640px; margin:0 auto; padding:0 20px 30px; font-size:13.5px; line-height:1.6; }
.explain b { color:var(--accent); }

/* ============ 8. RESPONSIVE ============ */
@media (max-width:860px) {
  .view { flex-direction:column; }
  aside { width:100%; max-height:46%; border-right:0; border-bottom:1px solid var(--line); }
  .search { max-width:none; flex-basis:100%; margin:0; }
  #legend { display:none; }
  .lanes { flex-direction:column; }
  .lane { width:100%; max-width:none; }
}
@media (prefers-reduced-motion:reduce) { * { transition:none !important; animation:none !important; } }
</style>
</head>
<body>

<header class="top glass">
  <div class="brand"><div class="logo">CC</div>CodeContext</div>
  <nav class="tabs">
    <button data-view="home" class="on">Overview</button>
    <button data-view="map">Explore</button>
    <button data-view="loops">Loops<em>{{SUMMARY_LOOPS}}</em></button>
  </nav>
  <div class="search">
    <i>&#9906;</i><input id="query" placeholder="Search class or method\u2026" autocomplete="off" spellcheck="false"><kbd>/</kbd>
    <div id="results"></div>
  </div>
  <button class="icon" id="theme" title="Toggle theme">&#9680;</button>
</header>

<main id="home" class="view"></main>

<main id="map" class="view hidden">
  <aside id="card" class="glass"></aside>
  <section class="stage grab" id="stage">
    <div class="lens-bar glass">
      <div class="pills">
        <button class="pill on" data-lens="connections">Connections</button>
        <button class="pill" data-lens="impact">Impact</button>
        <button class="pill" data-lens="calls">Calls</button>
      </div>
      <span class="lens-summary" id="lens-summary">Who uses this / what does it use?</span>
    </div>
    
    <div class="trail" id="trail"></div>
    
    <!-- Lens 1: Connections Graph -->
    <div id="lens-connections" style="flex:1;display:flex;flex-direction:column;position:relative;overflow:hidden;">
      <div class="hint glass" id="hint">
        <span><b>Left:</b> classes that use this one \u00b7 <b>Middle:</b> your pick \u00b7 <b>Right:</b> what it uses. Click any box to follow the trail.</span>
        <button id="hint-close" aria-label="Dismiss">\u00d7</button>
      </div>
      <svg id="graph"></svg>
      <div id="legend" class="glass">
        <span><i style="background:var(--in)"></i>uses this class</span>
        <span><i style="background:var(--accent)"></i>used by this class</span>
        <span><i style="background:var(--hi)"></i>part of a loop</span>
      </div>
    </div>

    <!-- Lens 2: Impact (Blast Radius) -->
    <div id="lens-impact" class="hidden"></div>

    <!-- Lens 3: Calls (Method Call Graph) -->
    <div id="lens-calls" class="hidden">
      <div class="method-strip" id="method-strip"></div>
      <svg id="method-graph"></svg>
      <div class="trace-panel" id="trace-panel"></div>
    </div>

    <!-- Floating Zoom Controls Dock -->
    <div class="zoom-dock glass" id="zoom-dock">
      <button id="zoom-in" title="Zoom in">+</button>
      <button id="zoom-reset" title="Reset (1:1)">1:1</button>
      <button id="zoom-out" title="Zoom out">&minus;</button>
    </div>
  </section>
</main>

<main id="loops" class="view hidden">
  <aside id="loop-list" class="glass"></aside>
  <section class="stage"><svg id="ring" style="min-width:600px"></svg><div class="explain" id="explain"></div></section>
</main>

""";

    private static final String HTML_PART_2 = """
<script>
/* ============ 1. CONFIG ============ */
const AREAS = {{AREAS}};
const DESCRIPTIONS = {{DESCRIPTIONS}};
const INTERFACES = new Set({{INTERFACES}});
const IMPLEMENTATIONS = {{IMPLEMENTATIONS}};
const AUTHOR_OVERRIDE = {};
const RISK = { high: 50, medium: 25 };
const SIDE_LIMIT = 8;
const SUMMARY = {{SUMMARY}};

/* ============ 2. DATA ============ */
const DATA = {
  rows: {{ROWS}},
  aliases: {{ALIASES}},
  edges: `{{EDGES}}`,
  loops: {{LOOPS}},
  methods: {{METHODS}},
  calls: `{{CALLS}}`
};

/* ============ 3. MODEL ============ */
const levelOf = risk => risk >= RISK.high ? 'HIGH' : risk >= RISK.medium ? 'MEDIUM' : 'LOW';

function buildModel({ rows, aliases, edges: edgeText, loops: loopText, methods: methodsData, calls: callsText }) {
  const resolve = key => aliases[key] || key;
  const loops = loopText.map(s => s.split(' ').map(resolve));
  loops.forEach(l => l.push(l[0]));
  const inLoop = new Set(loops.flat());

  const classes = rows.map(([id, area, risk, rank, dependents, dependencies, churn, commits, authors, importance, change]) => {
    const totalCoupling = dependents + dependencies;
    const instability = totalCoupling > 0 ? (dependencies / totalCoupling) : 0.0;
    return {
      id, area, risk, rank, dependents, dependencies, churn, commits, authors,
      kind: INTERFACES.has(id) ? 'Interface' : 'Class',
      level: levelOf(risk),
      inLoop: inLoop.has(id),
      instability,
      factors: [importance, inLoop.has(id) ? 100 : 0, change, AUTHOR_OVERRIDE[id] ?? (authors === 1 && churn > 0 ? 100 : 0)],
      callers: [], callees: [], methods: []
    };
  });
  const byId = Object.fromEntries(classes.map(c => [c.id, c]));

  edgeText.split(/\\s+/).forEach(token => {
    const m = token.match(/^(\\w+)>(\\w+):(\\d+)$/);
    const from = m && byId[resolve(m[1])], to = m && byId[resolve(m[2])];
    if (!from || !to || from === to) return;
    const edge = { from, to, calls: +m[3] };
    from.callees.push(edge);
    to.callers.push(edge);
  });

  // Method model for Calls Lens
  const methodMap = {};
  if (methodsData) {
    Object.entries(methodsData).forEach(([clsId, mList]) => {
      const parentClass = byId[clsId];
      const area = parentClass ? parentClass.area : 'default';
      const level = parentClass ? parentClass.level : 'LOW';
      mList.forEach(mName => {
        const mId = `${clsId}.${mName}`;
        methodMap[mId] = { id: mId, cls: clsId, name: mName, area, level, callers: [], callees: [] };
      });
    });
  }

  if (callsText) {
    callsText.split(/\\s+/).forEach(token => {
      const m = token.match(/^([^>]+)>([^:]+):L(\\d+)$/);
      if (!m) return;
      const fromId = m[1], toId = m[2], line = +m[3];
      if (!methodMap[fromId]) {
        const dot = fromId.indexOf('.');
        const cls = dot !== -1 ? fromId.substring(0, dot) : fromId;
        const name = dot !== -1 ? fromId.substring(dot + 1) : fromId;
        const parentClass = byId[cls];
        methodMap[fromId] = { id: fromId, cls, name, area: parentClass?.area || 'default', level: parentClass?.level || 'LOW', callers: [], callees: [] };
      }
      if (!methodMap[toId]) {
        const dot = toId.indexOf('.');
        const cls = dot !== -1 ? toId.substring(0, dot) : toId;
        const name = dot !== -1 ? toId.substring(dot + 1) : toId;
        const parentClass = byId[cls];
        methodMap[toId] = { id: toId, cls, name, area: parentClass?.area || 'default', level: parentClass?.level || 'LOW', callers: [], callees: [] };
      }
      const fromM = methodMap[fromId], toM = methodMap[toId];
      if (!fromM || !toM || fromM === toM) return;
      fromM.callees.push({ to: toM, calls: 1, line });
      toM.callers.push({ from: fromM, calls: 1, line });
    });
  }

  classes.forEach(c => {
    c.methods = Object.values(methodMap).filter(m => m.cls === c.id);
  });

  return { classes, byId, loops, methods: methodMap };
}

/* ============ 4. HELPERS ============ */
const $ = id => document.getElementById(id);
const model = buildModel(DATA);
const state = {
  selected: null, showAll: false, trail: [], loopIndex: 0,
  lens: 'connections', hops: 3, dir: 'in', method: null,
  hideBoilerplate: true, impactFilter: 'all', traceDir: 'forward',
  zoom: 1.0, panX: 0, panY: 0, isPanning: false, startX: 0, startY: 0
};

const riskColor = level => level === 'HIGH' ? 'var(--hi)' : level === 'MEDIUM' ? 'var(--med)' : 'var(--low)';
const truncate = (s, n) => s.length > n ? s.slice(0, n - 1) + '\u2026' : s;
const describe = c => DESCRIPTIONS[c.id] || `Part of the \u201c${AREAS[c.area] ? AREAS[c.area][1] : 'default'}\u201d area.`;
const byRisk = () => [...model.classes].sort((a, b) => b.risk - a.risk);

/** Plain-English fact list: [severity, sentence] */
function factsFor(c) {
  return [
    [c.dependents >= 10 ? 'hi' : c.dependents >= 4 ? 'med' : 'low', `${c.dependents} classes depend on it` + (c.dependents >= 10 ? '. A change here ripples widely.' : '.')],
    [c.inLoop ? 'hi' : 'low', c.inLoop ? 'Part of a loop: these classes depend on each other in circles.' : 'Not part of any loop.'],
    [c.churn >= 30 ? 'hi' : c.churn > 0 ? 'med' : 'low', c.churn ? `${c.churn} lines edited recently across ${c.commits} commit${c.commits > 1 ? 's' : ''}.` : 'Stable: no recent edits.'],
    [c.churn && c.authors === 1 ? 'med' : 'low', !c.churn ? 'No recent author data.' : c.authors === 1 ? 'Only one person has worked on it: a knowledge risk.' : `${c.authors} people have worked on it.`]
  ];
}
function adviceFor(c) {
  if (c.inLoop && c.dependents >= 8) return 'Handle with care. Many classes depend on this and it sits inside a loop. Add tests first, and consider an interface to break the loop.';
  if (c.inLoop) return 'It is tangled in a loop. Untangling it would make changes safer.';
  if (c.churn >= 30) return 'This changes often. A good candidate for extra tests and review.';
  if (c.dependents >= 10) return 'Widely used, so keep its public behaviour stable.';
  return 'Looks healthy. No action needed.';
}

/* ============ 5. BLAST RADIUS BFS ALGORITHM ============ */
const blastCache = new Map();
function computeBlastRadius(startClass, dir, maxHops) {
  const cacheKey = `${startClass.id}:${dir}:${maxHops}`;
  if (blastCache.has(cacheKey)) return blastCache.get(cacheKey);

  const levels = [];
  const visited = new Set([startClass.id]);
  let currentLevel = [startClass];
  let capped = false;

  for (let h = 0; h < maxHops; h++) {
    const nextLevel = [];
    currentLevel.forEach(node => {
      const neighbors = dir === 'in' ? node.callers.map(e => e.from) : node.callees.map(e => e.to);
      neighbors.forEach(neighbor => {
        if (!visited.has(neighbor.id)) {
          if (visited.size >= 5000) {
            capped = true;
            return;
          }
          visited.add(neighbor.id);
          nextLevel.push(neighbor);
        }
      });
    });
    if (nextLevel.length === 0 || capped) break;
    levels.push(nextLevel);
    currentLevel = nextLevel;
  }

  const totalAffected = visited.size - 1;
  const pct = model.classes.length > 1 ? (totalAffected / (model.classes.length - 1)) * 100 : 0;
  let band = 'Contained', bandColor = 'var(--low)';
  if (pct >= 60) { band = 'Critical'; bandColor = 'var(--hi)'; }
  else if (pct >= 30) { band = 'Wide'; bandColor = 'var(--hi)'; }
  else if (pct >= 10) { band = 'Moderate'; bandColor = 'var(--med)'; }

  const res = { levels, totalAffected, pct, band, bandColor, capped };
  blastCache.set(cacheKey, res);
  return res;
}

/* ============ 6. STATE ROUTER (HASH-BASED) ============ */
function syncUrl() {
  if (!state.selected) return;
  const hash = `#class=${encodeURIComponent(state.selected.id)}&lens=${state.lens}&hops=${state.hops}&dir=${state.dir}`;
  if (window.location.hash !== hash) {
    history.replaceState(null, '', hash);
  }
}

function parseUrlHash() {
  const h = window.location.hash.substring(1);
  if (!h) return false;
  const params = new URLSearchParams(h);
  const clsId = params.get('class');
  const lens = params.get('lens');
  const hops = params.get('hops');
  const dir = params.get('dir');

  if (clsId && model.byId[clsId]) {
    state.selected = model.byId[clsId];
    if (lens) state.lens = lens;
    if (hops) state.hops = Math.max(1, Math.min(6, +hops));
    if (dir) state.dir = dir;
    state.trail = [state.selected];
    showView('map');
    return true;
  }
  return false;
}

/* ============ 7. NAVIGATION ============ */
function setView(name) {
  ['home', 'map', 'loops'].forEach(v => $(v).classList.toggle('hidden', v !== name));
  document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('on', b.dataset.view === name));
}
function showView(name) {
  setView(name);
  if (name === 'map') state.selected ? renderExplore() : (model.classes.length ? openClass(model.classes[0].id) : null);
  if (name === 'loops') renderLoops();
}
function openClass(id) {
  const c = model.byId[id];
  if (!c) return;
  state.selected = c; state.showAll = false;
  state.method = c.methods.length ? c.methods.sort((a,b) => b.callers.length - a.callers.length)[0] : null;
  if (state.trail.at(-1) !== c) state.trail.push(c);
  if (state.trail.length > 6) state.trail.shift();
  resetZoom();
  setView('map'); renderExplore();
  syncUrl();
}
function openLoop(index) { state.loopIndex = index; setView('loops'); renderLoops(); }

function setLens(lens) {
  state.lens = lens;
  renderExplore();
  syncUrl();
}

/* ============ 8. ZOOM & PAN CONTROLS ============ */
function applyTransform() {
  const activeSvg = state.lens === 'calls' ? $('method-graph') : $('graph');
  if (activeSvg) {
    activeSvg.style.transform = `translate(${state.panX}px, ${state.panY}px) scale(${state.zoom})`;
  }
}
function resetZoom() {
  state.zoom = 1.0; state.panX = 0; state.panY = 0;
  applyTransform();
}
function zoomIn() {
  state.zoom = Math.min(state.zoom + 0.15, 2.5);
  applyTransform();
}
function zoomOut() {
  state.zoom = Math.max(state.zoom - 0.15, 0.4);
  applyTransform();
}

/* ============ 9. VIEW: OVERVIEW ============ */
function rowHtml(c) {
  const tags = (c.inLoop ? '<span class="tag" style="color:var(--hi)">in a loop</span>' : '')
             + (c.authors === 1 && c.churn > 0 ? '<span class="tag" style="color:var(--med)">one author</span>' : '');
  const color = riskColor(c.level);
  return `<button class="row" data-open="${c.id}">
    <div class="n"><b>${c.id}</b> ${tags}<p>${DESCRIPTIONS[c.id] || `Used by ${c.dependents} classes.`}</p></div>
    <div class="meter"><i style="width:${Math.min(c.risk, 100)}%;background:${color}"></i></div>
    <span class="score" style="color:${color}">${c.risk.toFixed(0)}</span></button>`;
}

function renderHome() {
  const top = byRisk(), hub = [...model.classes].sort((a, b) => b.dependents - a.dependents);
  const oneAuthor = model.classes.filter(c => c.authors === 1 && c.churn > 0).length;
  const areas = Object.entries(AREAS).map(([key, [pkg, title, blurb]]) => {
    const chips = model.classes.filter(c => c.area === key).sort((a, b) => b.risk - a.risk)
      .map(c => `<button class="chip" data-open="${c.id}"><i class="dot" style="background:${riskColor(c.level)}"></i>${truncate(c.id, 24)}</button>`).join('');
    return `<div class="area"><h3>${title}</h3><p>${blurb} <span class="mono">${pkg}</span></p>${chips}</div>`;
  }).join('');

  const topName = top.length ? top[0].id : 'None';
  const hubName = hub.length ? hub[0].id : 'None';
  const hubDeps = hub.length ? hub[0].dependents : 0;

  const topBlast = [...model.classes].map(c => {
    const b = computeBlastRadius(c, 'in', 4);
    return { class: c, blast: b };
  }).sort((a, b) => b.blast.totalAffected - a.blast.totalAffected).slice(0, 5);

  $('home').innerHTML = `
    <p class="eyebrow">CodeContext Architecture Report</p>
    <h1>Your codebase at a glance</h1>
    <p class="lead">${SUMMARY.critical === 0 ? 'No class is at critical risk, which is good news.' : `${SUMMARY.critical} classes are at critical risk.`} The things to watch are <b>${SUMMARY.loops} circular dependencies</b>, and heavily-used classes like <b>${topName}</b> that many others rely on. Start with the list below, or search for any class.</p>
    <div class="kpis">
      <div class="kpi"><h3>Classes</h3><b>${SUMMARY.classes}</b><small>the building blocks of the project</small></div>
      <div class="kpi"><h3>Connections</h3><b>${SUMMARY.connections.toLocaleString()}</b><small>times one class uses another</small></div>
      <div class="kpi"><h3>Critical risk</h3><b style="color:var(--low)">${SUMMARY.critical}</b><small>${SUMMARY.critical === 0 ? 'nothing at breaking point' : 'classes at critical risk'}</small></div>
      <button class="kpi" data-view="loops"><h3>Loops</h3><b style="color:var(--hi)">${SUMMARY.loops}</b><small>classes that depend on each other in circles. Tap to see them \u2192</small></button>
    </div>
    <h2>Where to look first</h2>${top.slice(0, 6).map(rowHtml).join('')}
    <h2>Most depended-on</h2>
    <p class="lead" style="margin-bottom:12px"><b>${hubName}</b> is used by ${hubDeps} other classes, so changing it affects the most code. ${oneAuthor} of the classes shown were recently edited by just one person.</p>
    ${hub.slice(0, 3).map(rowHtml).join('')}
    <h2>Biggest blast radius</h2>
    <p class="lead" style="margin-bottom:12px">Classes whose modifications ripple outward to affect the largest percentage of the codebase.</p>
    ${topBlast.map(item => `
      <button class="row" data-open="${item.class.id}" data-lens-jump="impact">
        <div class="n"><b>${item.class.id}</b><p>Could affect ${item.blast.totalAffected} of ${model.classes.length - 1} other classes (${item.blast.pct.toFixed(0)}%)</p></div>
        <div class="meter"><i style="width:${Math.min(item.blast.pct, 100)}%;background:${item.blast.bandColor}"></i></div>
        <span class="score" style="color:${item.blast.bandColor}">${item.blast.totalAffected}</span>
      </button>`).join('')}
    <h2>Explore by area</h2><div class="areas">${areas}</div>
    <p class="lead" style="margin-top:28px;font-size:12.5px">Showing ${model.classes.length} of ${SUMMARY.classes} classes.</p>`;
}

/* ============ 10. VIEW: EXPLORE ============ */
function renderCard(c) {
  const color = riskColor(c.level);
  const labels = ['Importance', 'Tangled in loops', 'Recent change', 'Single-author'];
  const loopButtons = model.loops.map((l, i) => [l, i]).filter(([l]) => l.includes(c.id)).slice(0, 4)
    .map(([l, i]) => `<button class="btn" data-loop="${i}">Loop #${i + 1} \u00b7 ${l.length - 1} classes \u2192</button>`).join('');
  const pkgFull = AREAS[c.area] ? AREAS[c.area][0] : '';
  const fqcn = pkgFull && pkgFull !== 'default' ? `${pkgFull}.${c.id}` : c.id;

  const blast = computeBlastRadius(c, 'in', state.hops);
  const implsList = IMPLEMENTATIONS[c.id] || [];
  const instabilityDesc = c.instability < 0.3 ? 'Stable Core' : c.instability < 0.7 ? 'Balanced' : 'Volatile / Dependent';

  $('card').innerHTML = `
    <span class="tag" style="color:${color}">${c.level} RISK</span><span class="tag" style="color:var(--accent)">${c.kind}</span>
    <h2>${c.id}</h2>
    <div class="fq mono">${fqcn}</div>
    <p class="desc">${describe(c)}</p>
    
    <div class="blast-card" data-open-impact="${c.id}">
      <div class="blast-head"><b>Blast radius</b><span class="tag" style="color:${blast.bandColor}">${blast.band}</span></div>
      <p>Changing this could affect <b>${blast.totalAffected} classes</b> (${blast.pct.toFixed(0)}%). <a href="javascript:void(0)" style="color:var(--accent)">See impact \u2192</a></p>
    </div>

    ${implsList.length ? `
      <div class="impl-box">
        <div class="impl-head">Implementations / Subtypes (${implsList.length})</div>
        <div>${implsList.slice(0, 6).map(subId => `<button class="chip" data-open="${subId}" style="margin:0 4px 4px 0">${truncate(subId, 22)}</button>`).join('')}${implsList.length > 6 ? `<span style="font-size:11px;color:var(--muted)">+ ${implsList.length - 6} more</span>` : ''}</div>
      </div>` : ''}

    <div class="meter" style="width:100%;height:8px"><i style="width:${Math.min(c.risk, 100)}%;background:${color}"></i></div>
    <div style="font-size:12px;color:var(--muted);margin-top:5px">Risk score <b style="color:var(--text)">${c.risk}</b> \u00b7 rank #${c.rank} of ${SUMMARY.classes}</div>
    <div class="stats">
      <div><b>${c.dependents}</b><small>classes use it</small></div>
      <div><b>${c.dependencies}</b><small>classes it uses</small></div>
      <div style="grid-column:span 2"><b style="font-size:16px">${c.instability.toFixed(2)}</b><small>Instability ratio \u00b7 ${instabilityDesc}</small></div>
    </div>
    <div class="advice"><b>What to do</b>${adviceFor(c)}</div>
    <ul class="facts">${factsFor(c).map(([sev, text]) => `<li><i class="dot" style="background:var(--${sev})"></i>${text}</li>`).join('')}</ul>
    <div class="bars"><div style="font-weight:600;color:var(--text)">Why this score?</div>
      ${labels.map((l, i) => `<div><span>${l}<b style="color:var(--text)">${c.factors[i].toFixed(0)}%</b></span><div class="meter"><i style="width:${c.factors[i]}%;background:var(--accent)"></i></div></div>`).join('')}</div>
    ${loopButtons ? `<div style="font-size:12px;color:var(--muted)">In ${model.loops.filter(l => l.includes(c.id)).length} loop(s):</div>${loopButtons}` : ''}`;
}

function renderExplore() {
  const c = state.selected;
  if (!c) return;
  renderCard(c);

  document.querySelectorAll('.lens-bar .pill').forEach(btn => {
    btn.classList.toggle('on', btn.dataset.lens === state.lens);
  });
  const summaries = {
    connections: 'Who uses this / what does it use?',
    impact: 'What could break if I change this?',
    calls: 'How does execution flow here?'
  };
  $('lens-summary').textContent = summaries[state.lens] || summaries.connections;

  $('lens-connections').classList.toggle('hidden', state.lens !== 'connections');
  $('lens-impact').classList.toggle('hidden', state.lens !== 'impact');
  $('lens-calls').classList.toggle('hidden', state.lens !== 'calls');
  $('zoom-dock').classList.toggle('hidden', state.lens === 'impact');

  $('trail').innerHTML = (state.trail.length > 1 ? '<button class="btn" data-back="1" style="margin:0">\u2190 Back</button>' : '')
    + state.trail.map((t, i) => i < state.trail.length - 1
        ? `<button class="chip" data-open="${t.id}" style="margin:0">${truncate(t.id, 18)}</button><span>\u203a</span>`
        : `<b style="color:var(--text)">${t.id}</b>`).join('');

  if (state.lens === 'connections') {
    renderConnectionsGraph(c);
  } else if (state.lens === 'impact') {
    renderImpactLens(c);
  } else if (state.lens === 'calls') {
    renderCallsLens(c);
  }
}

/* ============ 11. LENS 1: CONNECTIONS GRAPH ============ */
function renderConnectionsGraph(c) {
  const W = 230, H = 44, GAP = 14, X = [30, 385, 740];
  const take = list => {
    const sorted = [...list].sort((a, b) => b.calls - a.calls);
    const limit = state.showAll ? sorted.length : SIDE_LIMIT;
    return { items: sorted.slice(0, limit), hidden: Math.max(sorted.length - limit, 0) };
  };
  const left = take(c.callers), right = take(c.callees);
  const rows = side => side.items.length + (side.hidden ? 1 : 0);
  const height = Math.max(Math.max(rows(left), rows(right), 1) * (H + GAP) + 90, 330);
  const startY = side => (height - (rows(side) * (H + GAP) - GAP)) / 2 + 14;
  const centerY = height / 2 - H / 2;
  const twoWay = new Set(c.callers.map(e => e.from).filter(f => c.callees.some(e => e.to === f)));

  const nodeSvg = (m, x, y, extra = '', calls = 0) => `
    <g class="node ${extra}" data-open="${m.id}" transform="translate(${x},${y})" tabindex="0" role="button">
      <rect width="${W}" height="${H}" rx="12"/>
      <rect x="10" y="9" width="4" height="${H - 18}" rx="2" style="fill:${riskColor(m.level)}"/>
      <text x="24" y="19">${truncate(m.id, 24)}</text>
      <text class="sub" x="24" y="34">${AREAS[m.area] ? AREAS[m.area][1] : 'default'}${twoWay.has(m) ? ' \u00b7 two-way' : ''}</text>
      ${calls ? `<text class="count" x="${W - 12}" y="19">${calls}\u00d7</text>` : ''}
      ${m.inLoop ? `<circle cx="${W - 14}" cy="${H - 12}" r="4" style="fill:var(--hi)"/>` : ''}
    </g>`;
  const curve = (x1, y1, x2, y2, color, calls, marker) => {
    const mid = (x2 - x1) / 2, width = 1.3 + Math.log2(calls + 1) * 0.45;
    return `<path d="M${x1} ${y1}C${x1 + mid} ${y1} ${x2 - mid} ${y2} ${x2} ${y2}" fill="none" style="stroke:${color}" stroke-width="${width}" opacity=".55" marker-end="url(#${marker})"/>`;
  };
  const column = (side, x, isCallers) => {
    let y = startY(side), out = '';
    side.items.forEach(e => {
      const m = isCallers ? e.from : e.to;
      out += isCallers
        ? curve(X[0] + W, y + H / 2, X[1], centerY + H / 2, 'var(--in)', e.calls, 'arrow-in')
        : curve(X[1] + W, centerY + H / 2, X[2], y + H / 2, 'var(--accent)', e.calls, 'arrow-out');
      out += nodeSvg(m, x, y, '', e.calls);
      y += H + GAP;
    });
    if (side.hidden) out += `<g class="node more" data-more="1" transform="translate(${x},${y})" tabindex="0" role="button"><rect width="${W}" height="${H}" rx="12"/><text x="${W / 2}" y="27" style="text-anchor:middle;fill:var(--accent)">+ ${side.hidden} more</text></g>`;
    return out;
  };
  const arrow = (id, color) => `<marker id="${id}" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" style="fill:${color}"/></marker>`;
  const title = (i, text) => `<text class="col-title" x="${X[i] + W / 2}" y="20">${text}</text>`;

  $('graph').setAttribute('viewBox', `0 0 1000 ${height}`);
  $('graph').innerHTML = `<defs>${arrow('arrow-out', 'var(--accent)')}${arrow('arrow-in', 'var(--in)')}</defs>`
    + column(left, X[0], true) + column(right, X[2], false) + nodeSvg(c, X[1], centerY, 'selected')
    + title(0, `Used by (${c.callers.length})`) + title(1, 'Selected') + title(2, `Uses (${c.callees.length})`)
    + (left.items.length ? '' : `<text class="empty" x="${X[0] + W / 2}" y="${height / 2}">Nothing here calls it</text>`)
    + (right.items.length ? '' : `<text class="empty" x="${X[2] + W / 2}" y="${height / 2}">It calls nothing else</text>`);
  applyTransform();
}

/* ============ 12. LENS 2: IMPACT LENS (BLAST RADIUS) ============ */
function renderImpactLens(c) {
  const blast = computeBlastRadius(c, state.dir, state.hops);
  const otherCount = Math.max(model.classes.length - 1, 1);
  const actionText = state.dir === 'in' ? 'depend on' : 'relies on';
  const headlineVerb = state.dir === 'in' ? 'could affect' : 'relies directly or indirectly on';

  // Area breakdown across affected nodes
  const areaCounts = {};
  blast.levels.flat().forEach(node => {
    areaCounts[node.area] = (areaCounts[node.area] || 0) + 1;
  });
  const areaChips = Object.entries(areaCounts).map(([key, count]) => {
    const areaName = AREAS[key] ? AREAS[key][1] : 'default';
    return `<span class="chip" style="cursor:default"><b>${count}</b> ${areaName}</span>`;
  }).join('');

  // Ripple Lanes with Cutoff Filter
  const heatColors = ['var(--hi)', 'var(--med)', 'var(--accent)'];
  const lanesHtml = blast.levels.length === 0
    ? `<p style="padding:20px;color:var(--muted)">Nothing in this direction. The ripple stops here.</p>`
    : blast.levels.map((levelNodes, idx) => {
        const hopNum = idx + 1;
        const heatColor = heatColors[Math.min(idx, heatColors.length - 1)];
        const sub = hopNum === 1 ? 'direct' : 'transitive';

        let filteredNodes = levelNodes;
        if (state.impactFilter === 'highRisk') {
          filteredNodes = levelNodes.filter(n => n.level === 'HIGH');
        } else if (state.impactFilter === 'crossPackage') {
          filteredNodes = levelNodes.filter(n => n.area !== c.area);
        }

        const groups = {};
        filteredNodes.forEach(n => {
          (groups[n.area] = groups[n.area] || []).push(n);
        });

        const groupsHtml = Object.keys(groups).length === 0
          ? `<p style="font-size:11.5px;color:var(--muted);padding:8px 0">No classes match active filter.</p>`
          : Object.entries(groups).map(([aKey, nodes]) => {
              const aName = AREAS[aKey] ? AREAS[aKey][1] : 'default';
              const topNodes = nodes.slice(0, 4);
              const remNodes = nodes.slice(4);
              const topChips = topNodes.map(n => {
                const isPartner = n.inLoop && c.inLoop && model.loops.some(l => l.includes(n.id) && l.includes(c.id));
                return `
                  <button class="chip" data-open="${n.id}" title="${n.id}">
                    <i class="dot" style="background:${riskColor(n.level)}"></i>${truncate(n.id, 20)}
                    ${isPartner ? '<span class="tag" style="color:var(--hi);margin-left:4px;padding:1px 4px;font-size:9px">Loop</span>' : ''}
                  </button>`;
              }).join('');
              const remHtml = remNodes.length ? `
                <details>
                  <summary>+ ${remNodes.length} more</summary>
                  <div style="margin-top:4px">${remNodes.map(n => `
                    <button class="chip" data-open="${n.id}" title="${n.id}">
                      <i class="dot" style="background:${riskColor(n.level)}"></i>${truncate(n.id, 20)}
                    </button>`).join('')}</div>
                </details>` : '';
              return `
                <div class="lane-group">
                  <div class="lane-group-title"><span>${aName}</span><span>${nodes.length}</span></div>
                  ${topChips}${remHtml}
                </div>`;
            }).join('');

        return `
          <div class="lane" style="animation-delay:${idx * 120}ms">
            <div class="lane-bar" style="background:${heatColor}"></div>
            <div class="lane-title">
              <span>Hop ${hopNum} \u00b7 ${sub}</span>
              <span class="mono" style="color:var(--muted)">${filteredNodes.length}</span>
            </div>
            <div class="lane-content">${groupsHtml}</div>
          </div>`;
      }).join('');

  $('lens-impact').innerHTML = `
    <div class="impact-head">
      <div class="gauge" style="--c:${blast.bandColor};--pct:${blast.pct}">
        <div class="gauge-inner">${blast.pct.toFixed(0)}%</div>
      </div>
      <div class="impact-intro">
        <span class="tag" style="color:${blast.bandColor}">${blast.band.toUpperCase()} IMPACT</span>
        <h3>Changing ${c.id} ${headlineVerb} ${blast.totalAffected} classes${blast.capped ? ' (5,000+ capped)' : ''}</h3>
        <p>That is <b>${blast.pct.toFixed(0)}%</b> of the ${otherCount} other classes in this codebase that ${actionText} it, directly or through intermediate dependencies.</p>
      </div>
    </div>

    <div class="controls">
      <div class="seg">
        <button class="${state.dir === 'in' ? 'on' : ''}" data-dir="in">Who depends on it</button>
        <button class="${state.dir === 'out' ? 'on' : ''}" data-dir="out">What it relies on</button>
      </div>
      <div class="slider-wrap">
        <span>Hops: <b id="hops-val" style="color:var(--text)">${state.hops}</b></span>
        <input type="range" id="hops-slider" min="1" max="6" value="${state.hops}">
      </div>
      <div style="margin-left:auto;display:flex;gap:8px;">
        <button class="btn" id="copy-impact-btn" style="margin:0">Copy Plain List</button>
        <button class="btn" id="copy-md-btn" style="margin:0">Copy PR Checklist</button>
      </div>
    </div>

    <div class="filters-wrap">
      <span style="color:var(--muted);font-weight:600">Filter Lanes:</span>
      <button class="filter-btn ${state.impactFilter === 'all' ? 'on' : ''}" data-filter="all">All Affected</button>
      <button class="filter-btn ${state.impactFilter === 'highRisk' ? 'on' : ''}" data-filter="highRisk">High Risk Only</button>
      <button class="filter-btn ${state.impactFilter === 'crossPackage' ? 'on' : ''}" data-filter="crossPackage">Cross-Package Only</button>
    </div>

    <div class="area-summary">${areaChips}</div>
    <div class="lanes">${lanesHtml}</div>
    <p style="font-size:12px;color:var(--muted);margin-top:16px;">Static analysis \u00b7 Reflection and dynamic injection can introduce runtime dependencies.</p>`;

  const slider = $('hops-slider');
  if (slider) {
    slider.oninput = e => {
      state.hops = +e.target.value;
      $('hops-val').textContent = state.hops;
      renderImpactLens(state.selected);
      syncUrl();
    };
  }

  // Copy Plain Text Handler
  const copyImpactBtn = $('copy-text-btn');
  if (copyImpactBtn) {
    copyImpactBtn.onclick = () => {
      const LF = String.fromCharCode(10);
      let text = `Impact of changing ${c.id} (${blast.totalAffected} classes, ${blast.pct.toFixed(0)}% of codebase):` + LF;
      blast.levels.forEach((lvl, i) => {
        text += `Hop ${i + 1} (${lvl.length} classes): ` + lvl.map(n => n.id).join(', ') + LF;
      });
      navigator.clipboard?.writeText(text).then(() => {
        copyImpactBtn.textContent = 'Copied!';
        setTimeout(() => copyImpactBtn.textContent = 'Copy Plain List', 1600);
      });
    };
  }

  // Copy Markdown PR Checklist Handler
  const copyMdBtn = $('copy-md-btn');
  if (copyMdBtn) {
    copyMdBtn.onclick = () => {
      const LF = String.fromCharCode(10);
      const BT = '`';
      let md = `### Blast Radius Impact Analysis: ${BT}${c.id}${BT}` + LF;
      md += `> **Severity:** ${blast.band.toUpperCase()} (Affects ${blast.totalAffected} classes · ${blast.pct.toFixed(0)}% of codebase)` + LF + LF;
      blast.levels.forEach((lvl, i) => {
        const sub = i === 0 ? 'Direct Callers (Regression Risk)' : 'Transitive Dependents';
        md += `- [ ] **Hop ${i + 1}: ${sub}**` + LF;
        lvl.forEach(n => {
          md += `  - [ ] ${BT}${n.id}${BT}${n.level === 'HIGH' ? ' [HIGH RISK]' : ''}` + LF;
        });
      });
      navigator.clipboard?.writeText(md).then(() => {
        copyMdBtn.textContent = 'Copied PR Checklist!';
        setTimeout(() => copyMdBtn.textContent = 'Copy PR Checklist', 1600);
      });
    };
  }
}

/* ============ 13. LENS 3: CALLS LENS (METHOD CALL GRAPH) ============ */
const isBoilerplate = m => {
  const n = m.name;
  if (/^(get|set)[A-Z]/.test(n) && m.callees.length === 0) return true;
  if (/^is[A-Z]/.test(n) && m.callees.length === 0) return true;
  if (['equals', 'hashCode', 'toString', 'clone', 'finalize'].includes(n)) return true;
  return false;
};

function renderCallsLens(c) {
  const mList = c.methods || [];
  if (mList.length === 0) {
    $('method-strip').innerHTML = '<span style="font-size:12.5px;color:var(--muted)">No method call data recorded for this class.</span>';
    $('method-graph').setAttribute('viewBox', '0 0 1000 200');
    $('method-graph').innerHTML = '<text class="empty" x="500" y="100">No method calls recorded</text>';
    $('trace-panel').innerHTML = '';
    return;
  }

  if (!state.method || state.method.cls !== c.id) {
    state.method = mList.sort((a,b) => b.callers.length - a.callers.length)[0];
  }
  const currM = state.method;

  const visibleMethods = state.hideBoilerplate ? mList.filter(m => m === currM || !isBoilerplate(m)) : mList;
  const hiddenCount = mList.length - visibleMethods.length;

  $('method-strip').innerHTML = `
    <span style="font-size:11px;font-weight:600;color:var(--muted);text-transform:uppercase;margin-right:4px;">Methods:</span>
    <button class="filter-btn ${state.hideBoilerplate ? 'on' : ''}" id="toggle-boilerplate-btn" style="margin-right:8px;">
      ${state.hideBoilerplate ? `Hide Boilerplate (${hiddenCount})` : 'Showing All'}
    </button>`
    + visibleMethods.map(m => `
      <button class="method-chip ${m === currM ? 'on' : ''}" data-method="${m.id}">
        ${truncate(m.name, 24)}() <span class="mono" style="font-size:10px;color:var(--muted)">(${m.callers.length + m.callees.length})</span>
      </button>`).join('');

  // 3-Column Method Graph
  const W = 230, H = 44, GAP = 14, X = [30, 385, 740];
  const take = list => {
    const sorted = [...list].sort((a, b) => b.calls - a.calls);
    const limit = state.showAll ? sorted.length : SIDE_LIMIT;
    return { items: sorted.slice(0, limit), hidden: Math.max(sorted.length - limit, 0) };
  };
  const left = take(currM.callers), right = take(currM.callees);
  const rows = side => side.items.length + (side.hidden ? 1 : 0);
  const height = Math.max(Math.max(rows(left), rows(right), 1) * (H + GAP) + 90, 330);
  const startY = side => (height - (rows(side) * (H + GAP) - GAP)) / 2 + 14;
  const centerY = height / 2 - H / 2;

  const methodNodeSvg = (m, x, y, extra = '', label = '') => `
    <g class="node ${extra}" data-method-open="${m.id}" transform="translate(${x},${y})" tabindex="0" role="button">
      <rect width="${W}" height="${H}" rx="12"/>
      <rect x="10" y="9" width="4" height="${H - 18}" rx="2" style="fill:${riskColor(m.level)}"/>
      <text x="24" y="19">${truncate(m.name + '()', 22)}</text>
      <text class="sub" x="24" y="34">${truncate(m.cls, 24)}</text>
      ${label ? `<text class="count" x="${W - 12}" y="19">${label}</text>` : ''}
    </g>`;

  const curve = (x1, y1, x2, y2, color, label, marker, isDashed = false) => {
    const mid = (x2 - x1) / 2;
    const dash = isDashed ? 'stroke-dasharray="4 4"' : '';
    return `<path d="M${x1} ${y1}C${x1 + mid} ${y1} ${x2 - mid} ${y2} ${x2} ${y2}" fill="none" style="stroke:${color}" stroke-width="1.6" opacity=".6" ${dash} marker-end="url(#${marker})"/>`;
  };

  const column = (side, x, isCallers) => {
    let y = startY(side), out = '';
    side.items.forEach(e => {
      const m = isCallers ? e.from : e.to;
      const lbl = e.line ? `L${e.line}` : 'call';
      const isInterface = INTERFACES.has(m.cls);
      out += isCallers
        ? curve(X[0] + W, y + H / 2, X[1], centerY + H / 2, 'var(--in)', lbl, 'arrow-in', isInterface)
        : curve(X[1] + W, centerY + H / 2, X[2], y + H / 2, 'var(--accent)', lbl, 'arrow-out', isInterface);
      out += methodNodeSvg(m, x, y, '', lbl);
      y += H + GAP;
    });
    if (side.hidden) out += `<g class="node more" data-more="1" transform="translate(${x},${y})"><rect width="${W}" height="${H}" rx="12"/><text x="${W / 2}" y="27" style="text-anchor:middle;fill:var(--accent)">+ ${side.hidden} more</text></g>`;
    return out;
  };

  const arrow = (id, color) => `<marker id="${id}" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" style="fill:${color}"/></marker>`;
  const title = (i, text) => `<text class="col-title" x="${X[i] + W / 2}" y="20">${text}</text>`;

  $('method-graph').setAttribute('viewBox', `0 0 1000 ${height}`);
  $('method-graph').innerHTML = `<defs>${arrow('arrow-out', 'var(--accent)')}${arrow('arrow-in', 'var(--in)')}</defs>`
    + column(left, X[0], true) + column(right, X[2], false) + methodNodeSvg(currM, X[1], centerY, 'selected')
    + title(0, `Called by (${currM.callers.length})`) + title(1, 'Selected Method') + title(2, `Calls (${currM.callees.length})`)
    + (left.items.length ? '' : `<text class="empty" x="${X[0] + W / 2}" y="${height / 2}">No direct callers</text>`)
    + (right.items.length ? '' : `<text class="empty" x="${X[2] + W / 2}" y="${height / 2}">Calls no other methods</text>`);
  applyTransform();

  // Trace Path UI Section
  const otherMethods = Object.values(model.methods).filter(m => m !== currM && m.cls !== currM.cls).slice(0, 150);
  const optionsHtml = otherMethods.map(m => `<option value="${m.id}">${m.id}()</option>`).join('');

  $('trace-panel').innerHTML = `
    <div class="trace-controls">
      <span>Trace path from <b class="mono" style="color:var(--text)">${currM.name}()</b> to:</span>
      <select id="trace-target" style="padding:4px 8px;border-radius:6px;border:1px solid var(--line);background:var(--bg);color:var(--text);font:inherit;font-size:12px;outline:none;">
        ${optionsHtml}
      </select>
      <div class="seg">
        <button class="${state.traceDir === 'forward' ? 'on' : ''}" id="trace-fwd-btn">Forward (Calls)</button>
        <button class="${state.traceDir === 'reverse' ? 'on' : ''}" id="trace-rev-btn">Reverse (Callers)</button>
      </div>
      <button class="btn" id="trace-btn" style="margin:0">Find path</button>
    </div>
    <div id="trace-result"></div>`;

  $('toggle-boilerplate-btn').onclick = () => {
    state.hideBoilerplate = !state.hideBoilerplate;
    renderCallsLens(state.selected);
  };
  $('trace-fwd-btn').onclick = () => { state.traceDir = 'forward'; $('trace-fwd-btn').classList.add('on'); $('trace-rev-btn').classList.remove('on'); };
  $('trace-rev-btn').onclick = () => { state.traceDir = 'reverse'; $('trace-rev-btn').classList.add('on'); $('trace-fwd-btn').classList.remove('on'); };

  $('trace-btn').onclick = () => {
    const targetId = $('trace-target').value;
    const targetM = model.methods[targetId];
    if (!targetM) return;
    const path = findMethodPath(currM, targetM, state.traceDir);
    if (!path) {
      $('trace-result').innerHTML = '<span style="color:var(--hi);font-size:12.5px;padding-top:4px;display:block">No call path found in this direction.</span>';
    } else {
      let narrative = `<div class="narrative"><b>Shortest Path (${path.length - 1} calls):</b><br/>`;
      path.forEach((step, idx) => {
        if (idx === 0) {
          narrative += `1. <b style="color:var(--text)">${step.method.cls}.${step.method.name}()</b> [Source]<br/>`;
        } else {
          narrative += `&nbsp;&nbsp;&nbsp;└── calls <b style="color:var(--text)">${step.method.cls}.${step.method.name}()</b> at line ${step.line}<br/>`;
        }
      });
      narrative += '</div>';
      $('trace-result').innerHTML = narrative;
    }
  };
}

// Method BFS Path Finder (Forward or Reverse)
function findMethodPath(startM, targetM, direction = 'forward') {
  const queue = [[{ method: startM, line: 0 }]];
  const visited = new Set([startM.id]);

  while (queue.length > 0) {
    const path = queue.shift();
    const last = path[path.length - 1].method;
    if (last.id === targetM.id) return path;

    const edges = direction === 'forward' ? last.callees : last.callers;
    for (const edge of edges) {
      const neighbor = direction === 'forward' ? edge.to : edge.from;
      if (!visited.has(neighbor.id)) {
        visited.add(neighbor.id);
        queue.push([...path.slice(0, -1), { method: last, line: edge.line }, { method: neighbor, line: 0 }]);
      }
    }
  }
  return null;
}

/* ============ 14. VIEW: LOOPS ============ */
function renderLoops() {
  if (!model.loops.length) {
    $('loop-list').innerHTML = `<h2>Circular dependencies</h2>
      <p class="desc" style="font-size:13px;color:var(--muted)">No circular dependencies found in this codebase.</p>`;
    $('ring').innerHTML = '';
    $('explain').innerHTML = '<p>Your architecture is completely acyclic.</p>';
    return;
  }
  $('loop-list').innerHTML = `<h2>Circular dependencies</h2>
    <p class="desc" style="font-size:13px;color:var(--muted)">Each card is a chain of classes that eventually depends on itself. Pick one to see it.</p>`
    + model.loops.map((l, i) => `<button class="loop-card ${i === state.loopIndex ? 'on' : ''}" data-loop="${i}">
        <b>Loop #${i + 1} \u00b7 ${l.length - 1} classes</b><p>${l.slice(0, -1).map(n => truncate(n, 18)).join(' \u2192 ')} \u2192 \u2026</p></button>`).join('');

  const names = model.loops[state.loopIndex].slice(0, -1), k = names.length;
  const W = 190, H = 38, cx = 350, cy = 235;
  const radius = k === 2 ? 110 : 165, stretch = k === 2 ? 1.6 : 1.15;
  const pts = names.map((id, i) => {
    const a = -Math.PI / 2 + i * 2 * Math.PI / k;
    return { id, x: cx + radius * Math.cos(a) * stretch, y: cy + radius * Math.sin(a) };
  });
  let svg = '<defs><marker id="arrow-loop" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="8" markerHeight="8" orient="auto"><path d="M0 0L8 4L0 8z" style="fill:var(--hi)"/></marker></defs>';
  pts.forEach((p, i) => {
    const q = pts[(i + 1) % k], dx = q.x - p.x, dy = q.y - p.y, d = Math.hypot(dx, dy), ux = dx / d, uy = dy / d;
    const inset = Math.abs(ux) * H > Math.abs(uy) * W ? W / 2 + 4 : H / 2 + 4;
    const ox = -uy * 8, oy = ux * 8;
    svg += `<line x1="${p.x + ux * inset + ox}" y1="${p.y + uy * inset + oy}" x2="${q.x - ux * inset + ox}" y2="${q.y - uy * inset + oy}" style="stroke:var(--hi)" stroke-width="2.2" marker-end="url(#arrow-loop)"/>`;
  });
  pts.forEach(p => svg += `<g class="node" data-open="${p.id}" transform="translate(${p.x - W / 2},${p.y - H / 2})" tabindex="0" role="button"><rect width="${W}" height="${H}" rx="11"/><text x="${W / 2}" y="24" style="text-anchor:middle">${truncate(p.id, 24)}</text></g>`);
  $('ring').setAttribute('viewBox', '0 0 700 470');
  $('ring').innerHTML = svg;

  $('explain').innerHTML = `
    <p><b>What this means.</b> ${names.join(' \u2192 ')} \u2192 ${names[0]}. Each class needs the next one, and the last needs the first, so none can be changed or tested in isolation.</p>
    <p style="margin-top:10px"><b>Why it matters.</b> A change in any one of them can ripple around the whole loop.</p>
    <p style="margin-top:10px"><b>How to fix it.</b> Pull the shared part out into a small interface, or let one class depend on an abstraction instead of the other class directly. Click any box to inspect it.</p>`;
}

/* ============ 15. EVENTS & INTERACTION ============ */
document.addEventListener('click', e => {
  const t = e.target.closest('[data-open],[data-view],[data-loop],[data-more],[data-back],[data-lens],[data-open-impact],[data-lens-jump],[data-dir],[data-filter],[data-method],[data-method-open]');
  if (!t) return;
  const d = t.dataset;
  if (d.open) {
    openClass(d.open);
    if (d.lensJump) setLens(d.lensJump);
  }
  else if (d.view) showView(d.view);
  else if (d.loop !== undefined) openLoop(+d.loop);
  else if (d.more) { state.showAll = true; renderExplore(); }
  else if (d.back) { state.trail.pop(); if (state.trail.length) openClass(state.trail.pop().id); }
  else if (d.lens) setLens(d.lens);
  else if (d.openImpact) { openClass(d.openImpact); setLens('impact'); }
  else if (d.dir) { state.dir = d.dir; renderImpactLens(state.selected); syncUrl(); }
  else if (d.filter) { state.impactFilter = d.filter; renderImpactLens(state.selected); }
  else if (d.method) { state.method = model.methods[d.method]; renderCallsLens(state.selected); }
  else if (d.methodOpen) {
    const m = model.methods[d.methodOpen];
    if (m) {
      if (m.cls !== state.selected.id) openClass(m.cls);
      state.method = m;
      setLens('calls');
    }
  }
});

document.addEventListener('keydown', e => {
  if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('.node')) { e.preventDefault(); e.target.dispatchEvent(new MouseEvent('click', { bubbles: true })); }
  if (e.key === '/' && document.activeElement !== $('query')) { e.preventDefault(); $('query').focus(); }
});
$('hint-close').onclick = () => $('hint').style.display = 'none';
$('theme').onclick = () => {
  const root = document.documentElement;
  const current = root.dataset.theme || (matchMedia('(prefers-color-scheme:light)').matches ? 'light' : 'dark');
  root.dataset.theme = current === 'dark' ? 'light' : 'dark';
};

// Zoom Dock Events
$('zoom-in').onclick = zoomIn;
$('zoom-out').onclick = zoomOut;
$('zoom-reset').onclick = resetZoom;

// Canvas Pan & Mousewheel Zoom
const stageEl = $('stage');
stageEl.addEventListener('wheel', e => {
  if (state.lens === 'impact') return; // Natural scroll for impact lanes
  e.preventDefault();
  if (e.deltaY < 0) zoomIn();
  else zoomOut();
}, { passive: false });

stageEl.addEventListener('mousedown', e => {
  if (state.lens === 'impact' || e.target.closest('button, input, select, .zoom-dock, .lens-bar, .hint, .trail, .method-strip, .trace-panel')) return;
  state.isPanning = true;
  state.startX = e.clientX - state.panX;
  state.startY = e.clientY - state.panY;
  stageEl.classList.remove('grab');
  stageEl.classList.add('grabbing');
});

window.addEventListener('mousemove', e => {
  if (!state.isPanning) return;
  state.panX = e.clientX - state.startX;
  state.panY = e.clientY - state.startY;
  applyTransform();
});

window.addEventListener('mouseup', () => {
  if (state.isPanning) {
    state.isPanning = false;
    stageEl.classList.remove('grabbing');
    stageEl.classList.add('grab');
  }
});

/* Search (Classes & Methods) */
let matches = [];
const pick = (id, isMethod = false) => {
  $('query').value = ''; $('results').style.display = 'none'; $('query').blur();
  if (isMethod) {
    const m = model.methods[id];
    if (m) {
      openClass(m.cls);
      state.method = m;
      setLens('calls');
    }
  } else {
    openClass(id);
  }
};

$('query').oninput = e => {
  const q = e.target.value.trim().toLowerCase();
  if (!q) { $('results').style.display = 'none'; matches = []; return; }
  
  const classMatches = model.classes.filter(c => c.id.toLowerCase().includes(q)).slice(0, 4).map(c => ({ id: c.id, label: c.id, sub: AREAS[c.area] ? AREAS[c.area][1] : 'Class', isMethod: false }));
  const methodMatches = Object.values(model.methods).filter(m => m.name.toLowerCase().includes(q) || m.id.toLowerCase().includes(q)).slice(0, 4).map(m => ({ id: m.id, label: `${m.name}()`, sub: `Method · ${m.cls}`, isMethod: true }));
  
  matches = [...classMatches, ...methodMatches].slice(0, 6);
  $('results').style.display = matches.length ? 'block' : 'none';
  $('results').innerHTML = matches.map(item => `
    <button data-pick="${item.id}" data-is-method="${item.isMethod}">
      ${item.label}<span>${item.sub}</span>
    </button>`).join('');
};

$('results').onclick = e => {
  const b = e.target.closest('[data-pick]');
  if (b) pick(b.dataset.pick, b.dataset.isMethod === 'true');
};

$('query').onkeydown = e => {
  if (e.key === 'Enter' && matches[0]) pick(matches[0].id, matches[0].isMethod);
  if (e.key === 'Escape') { $('query').value = ''; $('results').style.display = 'none'; $('query').blur(); }
};

window.addEventListener('hashchange', () => {
  parseUrlHash();
});

/* ============ 16. INIT ============ */
if (!parseUrlHash()) {
  renderHome();
}
</script>
</body>
</html>
""";

    private static final String HTML_TEMPLATE = new StringBuilder(HTML_PART_1).append(HTML_PART_2).toString();
}