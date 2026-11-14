package com.codecontext.cli.export.security;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.finding.RuleMetadata;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.inventory.CryptoAsset;
import com.codecontext.core.security.inventory.CryptoAssetUsage;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.pqc.PqcAssessmentReport;
import com.codecontext.core.security.pqc.PqcAssetAssessment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates an enterprise-grade, standalone, zero-dependency, interactive HTML security dashboard.
 * Completely independent of architectural graph visualizers.
 * Fully protects against HTML injection and XSS from untrusted repository strings.
 */
public class SecurityHtmlReportExporter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    private final ObjectMapper mapper = new ObjectMapper();

    public void export(SecurityReportPayload payload, Path outputPath) throws IOException {
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        String html = generateHtml(payload);
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    public String generateHtml(SecurityReportPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");

        String safeJsonPayload = serializeSafePayloadJson(payload);
        String formattedTimestamp = ISO_FORMATTER.format(payload.scanTimestamp());

        // Overview counts
        int totalFindings = payload.analysisResult().findings().size();
        long highCount = payload.analysisResult().findings().stream()
                .filter(f -> f.severity() == FindingSeverity.HIGH || f.severity() == FindingSeverity.CRITICAL)
                .count();
        long medCount = payload.analysisResult().findings().stream()
                .filter(f -> f.severity() == FindingSeverity.MEDIUM)
                .count();
        long lowCount = payload.analysisResult().findings().stream()
                .filter(f -> f.severity() == FindingSeverity.LOW || f.severity() == FindingSeverity.INFO)
                .count();

        int pqcVulnerableCount = payload.pqcReport().map(PqcAssessmentReport::quantumVulnerableCount).orElse(0);
        int pqcImpactedCount = payload.pqcReport().map(PqcAssessmentReport::quantumImpactedCount).orElse(0);
        int pqcClassicallyWeakCount = payload.pqcReport().map(PqcAssessmentReport::classicallyWeakCount).orElse(0);
        int pqcSafeCount = payload.pqcReport().map(PqcAssessmentReport::quantumSafeCount).orElse(0);
        int pqcUnknownCount = payload.pqcReport().map(PqcAssessmentReport::unknownOrUnresolvedCount).orElse(0);
        int pqcAffectedClassesCount = payload.pqcReport().map(PqcAssessmentReport::totalAffectedClassesInBlastRadius).orElse(0);

        CryptoInventory inv = payload.cryptoInventory();

        String template = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CodeContext Security & Cryptographic Dashboard</title>
    <style>
        :root {
            --bg-main: #0f172a;
            --bg-card: #1e293b;
            --bg-card-hover: #334155;
            --border: #334155;
            --border-subtle: #1e293b;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --accent: #6366f1;
            --accent-hover: #4f46e5;
            --critical: #ef4444;
            --high: #f97316;
            --medium: #f59e0b;
            --low: #3b82f6;
            --clean: #10b981;
            --unknown: #64748b;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-main);
            color: var(--text-main);
            line-height: 1.5;
            padding: 24px;
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 24px;
            border-bottom: 1px solid var(--border);
            margin-bottom: 24px;
        }
        .header-title h1 {
            font-size: 24px;
            font-weight: 700;
            color: var(--text-main);
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .badge-version {
            font-size: 12px;
            padding: 2px 8px;
            border-radius: 9999px;
            background-color: var(--accent);
            color: #fff;
        }
        .header-meta {
            font-size: 13px;
            color: var(--text-muted);
            text-align: right;
        }
        .grid-overview {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }
        .card {
            background-color: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 16px;
        }
        .card-label {
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-muted);
            margin-bottom: 8px;
        }
        .card-value {
            font-size: 26px;
            font-weight: 700;
            color: var(--text-main);
        }
        .nav-tabs {
            display: flex;
            gap: 8px;
            border-bottom: 1px solid var(--border);
            margin-bottom: 24px;
        }
        .tab-btn {
            background: none;
            border: none;
            color: var(--text-muted);
            font-size: 14px;
            font-weight: 600;
            padding: 10px 16px;
            cursor: pointer;
            border-bottom: 2px solid transparent;
            transition: all 0.2s;
        }
        .tab-btn.active {
            color: var(--accent);
            border-bottom-color: var(--accent);
        }
        .tab-btn:hover {
            color: var(--text-main);
        }
        .tab-content { display: none; }
        .tab-content.active { display: block; }
        .controls-bar {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            margin-bottom: 16px;
            align-items: center;
        }
        .search-input, .filter-select {
            background-color: var(--bg-card);
            border: 1px solid var(--border);
            color: var(--text-main);
            padding: 8px 12px;
            border-radius: 6px;
            font-size: 14px;
            outline: none;
        }
        .search-input { min-width: 260px; flex: 1; }
        .search-input:focus, .filter-select:focus { border-color: var(--accent); }
        .table-container {
            overflow-x: auto;
            border: 1px solid var(--border);
            border-radius: 8px;
            background-color: var(--bg-card);
        }
        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 13px;
        }
        th {
            background-color: #1e293b;
            padding: 12px 16px;
            font-weight: 600;
            color: var(--text-muted);
            border-bottom: 1px solid var(--border);
            cursor: pointer;
            user-select: none;
        }
        th:hover { color: var(--text-main); }
        td {
            padding: 12px 16px;
            border-bottom: 1px solid var(--border-subtle);
            color: var(--text-main);
        }
        tr:hover td { background-color: rgba(255, 255, 255, 0.02); }
        tr.clickable { cursor: pointer; }
        .badge {
            display: inline-block;
            font-size: 11px;
            font-weight: 700;
            padding: 3px 8px;
            border-radius: 4px;
            text-transform: uppercase;
        }
        .badge-CRITICAL, .badge-HIGH { background-color: rgba(239, 68, 68, 0.2); color: var(--critical); border: 1px solid var(--critical); }
        .badge-MEDIUM { background-color: rgba(245, 158, 11, 0.2); color: var(--medium); border: 1px solid var(--medium); }
        .badge-LOW, .badge-INFO { background-color: rgba(59, 130, 246, 0.2); color: var(--low); border: 1px solid var(--low); }
        .badge-clean { background-color: rgba(16, 185, 129, 0.2); color: var(--clean); border: 1px solid var(--clean); }
        .badge-unknown { background-color: rgba(100, 116, 139, 0.2); color: var(--unknown); border: 1px solid var(--unknown); }
        .badge-QUANTUM_VULNERABLE { background-color: rgba(239, 68, 68, 0.2); color: var(--critical); border: 1px solid var(--critical); }
        .badge-QUANTUM_IMPACTED { background-color: rgba(245, 158, 11, 0.2); color: var(--medium); border: 1px solid var(--medium); }
        .badge-CLASSICALLY_WEAK { background-color: rgba(217, 70, 239, 0.2); color: #d946ef; border: 1px solid #d946ef; }
        .badge-QUANTUM_SAFE { background-color: rgba(16, 185, 129, 0.2); color: var(--clean); border: 1px solid var(--clean); }
        .badge-REVIEW_REQUIRED { background-color: rgba(245, 158, 11, 0.2); color: var(--medium); border: 1px solid var(--medium); }
        .badge-PRIORITY_UNDETERMINED { background-color: rgba(100, 116, 139, 0.2); color: var(--unknown); border: 1px solid var(--unknown); }
        .badge-UNKNOWN { background-color: rgba(100, 116, 139, 0.2); color: var(--unknown); border: 1px solid var(--unknown); }
        .detail-row { display: none; background-color: #0b1120; }
        .detail-row.expanded { display: table-row; }
        .detail-box {
            padding: 16px;
            font-size: 13px;
            display: grid;
            gap: 12px;
        }
        .detail-section-title { font-weight: 700; color: var(--text-muted); font-size: 11px; text-transform: uppercase; }
        .code-snippet {
            background-color: #0f172a;
            padding: 8px 12px;
            border-radius: 4px;
            font-family: monospace;
            border: 1px solid var(--border);
            color: #38bdf8;
            overflow-x: auto;
        }
        .charts-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
            gap: 20px;
            margin-bottom: 24px;
        }
        .chart-card {
            background-color: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 20px;
        }
        .chart-title {
            font-size: 14px;
            font-weight: 700;
            margin-bottom: 16px;
            color: var(--text-main);
        }
        .bar-chart-row {
            display: flex;
            align-items: center;
            margin-bottom: 8px;
            font-size: 12px;
        }
        .bar-chart-label { width: 140px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: var(--text-muted); }
        .bar-chart-track { flex: 1; background-color: #0f172a; height: 16px; border-radius: 4px; overflow: hidden; margin: 0 8px; }
        .bar-chart-fill { height: 100%; border-radius: 4px; }
        .bar-chart-val { width: 36px; text-align: right; font-weight: 600; }
        .limitations-box {
            background-color: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 20px;
            font-size: 14px;
        }
        .limitations-box h3 { margin-bottom: 12px; color: var(--medium); font-size: 16px; }
        .limitations-box ul { padding-left: 20px; color: var(--text-muted); }
        .limitations-box li { margin-bottom: 8px; }
    </style>
</head>
<body>

    <header class="header">
        <div class="header-title">
            <h1>CodeContext Security Intelligence <span class="badge-version">v{{SCANNER_VERSION}}</span></h1>
        </div>
        <div class="header-meta">
            <div>Repository: <strong>{{REPOSITORY_NAME}}</strong></div>
            <div>Scanned: {{FORMATTED_TIMESTAMP}}</div>
        </div>
    </header>

    <!-- SECTION A: OVERVIEW -->
    <section class="grid-overview">
        <div class="card">
            <div class="card-label">Files Scanned</div>
            <div class="card-value">{{FILES_SCANNED}}</div>
        </div>
        <div class="card">
            <div class="card-label">Security Findings</div>
            <div class="card-value" style="color: {{FINDINGS_COLOR}};">
                {{TOTAL_FINDINGS}}
            </div>
        </div>
        <div class="card">
            <div class="card-label">High / Critical</div>
            <div class="card-value" style="color: var(--critical);">{{HIGH_COUNT}}</div>
        </div>
        <div class="card">
            <div class="card-label">Medium Severity</div>
            <div class="card-value" style="color: var(--medium);">{{MED_COUNT}}</div>
        </div>
        <div class="card">
            <div class="card-label">Crypto Usages</div>
            <div class="card-value">{{TOTAL_OBSERVATIONS}}</div>
        </div>
        <div class="card">
            <div class="card-label">Unique Assets</div>
            <div class="card-value">{{UNIQUE_ASSETS}}</div>
        </div>
        <div class="card">
            <div class="card-label">Unresolved Usages</div>
            <div class="card-value" style="color: {{UNRESOLVED_COLOR}};">
                {{UNRESOLVED_COUNT}}
            </div>
        </div>
    </section>

    <!-- NAVIGATION TABS -->
    <nav class="nav-tabs">
        <button class="tab-btn active" onclick="switchTab('tab-findings', this)">Security Findings ({{TOTAL_FINDINGS}})</button>
        <button class="tab-btn" onclick="switchTab('tab-inventory', this)">Cryptographic Inventory ({{INVENTORY_ASSETS_COUNT}})</button>
        <button class="tab-btn" onclick="switchTab('tab-pqc', this)">PQC Migration ({{PQC_VULNERABLE_COUNT}})</button>
        <button class="tab-btn" onclick="switchTab('tab-visualizations', this)">Visualizations</button>
        <button class="tab-btn" onclick="switchTab('tab-unresolved', this)">Unresolved Observations ({{UNRESOLVED_COUNT}})</button>
        <button class="tab-btn" onclick="switchTab('tab-limitations', this)">Analysis Limitations</button>
    </nav>

    <!-- SECTION B: FINDINGS TAB -->
    <div id="tab-findings" class="tab-content active">
        <div class="controls-bar">
            <input type="text" id="finding-search" class="search-input" placeholder="Search by Rule, Message, File, or Algorithm..." oninput="renderFindings()">
            <select id="severity-filter" class="filter-select" onchange="renderFindings()">
                <option value="ALL">All Severities</option>
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
            </select>
            <select id="confidence-filter" class="filter-select" onchange="renderFindings()">
                <option value="ALL">All Confidences</option>
                <option value="HIGH">High Confidence</option>
                <option value="MEDIUM">Medium Confidence</option>
                <option value="LOW">Low Confidence</option>
            </select>
        </div>
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th onclick="sortFindings('severity')">Severity</th>
                        <th onclick="sortFindings('ruleId')">Rule ID</th>
                        <th onclick="sortFindings('ruleName')">Rule Name</th>
                        <th onclick="sortFindings('filePath')">File & Line</th>
                        <th onclick="sortFindings('confidence')">Confidence</th>
                        <th>CWE</th>
                    </tr>
                </thead>
                <tbody id="findings-tbody"></tbody>
            </table>
        </div>
    </div>

    <!-- SECTION C: INVENTORY TAB -->
    <div id="tab-inventory" class="tab-content">
        <div class="controls-bar">
            <input type="text" id="inventory-search" class="search-input" placeholder="Search by Asset Name or Family..." oninput="renderInventory()">
            <select id="primitive-filter" class="filter-select" onchange="renderInventory()">
                <option value="ALL">All Primitives</option>
                <option value="BLOCK_CIPHER">Block Cipher</option>
                <option value="STREAM_CIPHER">Stream Cipher</option>
                <option value="MESSAGE_DIGEST">Message Digest</option>
                <option value="MAC">MAC</option>
                <option value="SIGNATURE">Signature</option>
                <option value="TLS_PROTOCOL">TLS Protocol</option>
            </select>
        </div>
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>Asset Name</th>
                        <th>Family</th>
                        <th>Primitive</th>
                        <th>Mode / Padding</th>
                        <th>Usages</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody id="inventory-tbody"></tbody>
            </table>
        </div>
    </div>

    <!-- SECTION PQC: POST-QUANTUM CRYPTOGRAPHY MIGRATION TAB -->
    <div id="tab-pqc" class="tab-content">
        <div class="grid-overview" style="margin-bottom: 20px;">
            <div class="card">
                <div class="card-label">Quantum Vulnerable</div>
                <div class="card-value" style="color: var(--critical);">{{PQC_VULNERABLE_COUNT}}</div>
            </div>
            <div class="card">
                <div class="card-label">Quantum Impacted</div>
                <div class="card-value" style="color: var(--medium);">{{PQC_IMPACTED_COUNT}}</div>
            </div>
            <div class="card">
                <div class="card-label">Classically Weak</div>
                <div class="card-value" style="color: #d946ef;">{{PQC_CLASSICALLY_WEAK_COUNT}}</div>
            </div>
            <div class="card">
                <div class="card-label">Quantum Safe (PQC)</div>
                <div class="card-value" style="color: var(--clean);">{{PQC_SAFE_COUNT}}</div>
            </div>
            <div class="card">
                <div class="card-label">Affected Caller Classes</div>
                <div class="card-value" style="color: #38bdf8;">{{PQC_AFFECTED_CLASSES_COUNT}}</div>
            </div>
        </div>

        <div class="controls-bar">
            <input type="text" id="pqc-search" class="search-input" placeholder="Search by Asset Name, Purpose, NIST Target, or Caller Class..." oninput="renderPqc()">
            <select id="pqc-category-filter" class="filter-select" onchange="renderPqc()">
                <option value="ALL">All Categories</option>
                <option value="PQC_MIGRATION_CANDIDATE">PQC Migration Candidates</option>
                <option value="CLASSICAL_REMEDIATION">Classical Remediation</option>
                <option value="QUANTUM_CONSIDERATION">Modern Symmetric/Hash Considerations</option>
                <option value="UNKNOWN_OR_UNRESOLVED">Unknown / Unresolved</option>
            </select>
            <select id="pqc-exposure-filter" class="filter-select" onchange="renderPqc()">
                <option value="ALL">All Exposure Levels</option>
                <option value="QUANTUM_VULNERABLE">Quantum Vulnerable (Shor)</option>
                <option value="QUANTUM_IMPACTED">Quantum Impacted (Grover/BHT)</option>
                <option value="CLASSICALLY_WEAK">Classically Weak / Broken</option>
                <option value="QUANTUM_SAFE">Quantum Safe (NIST PQC)</option>
                <option value="UNKNOWN">Unknown Exposure</option>
            </select>
            <select id="pqc-priority-filter" class="filter-select" onchange="renderPqc()">
                <option value="ALL">All Priorities</option>
                <option value="REVIEW_REQUIRED">Review Required</option>
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
                <option value="PRIORITY_UNDETERMINED">Priority Undetermined</option>
            </select>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th onclick="sortPqc('name')">Asset / Algorithm</th>
                        <th onclick="sortPqc('exposureLevel')">Exposure Level</th>
                        <th onclick="sortPqc('purpose')">Cryptographic Purpose</th>
                        <th onclick="sortPqc('priority')">Priority</th>
                        <th>Target NIST Standard</th>
                        <th>Direct / Transitive Blast Radius</th>
                    </tr>
                </thead>
                <tbody id="pqc-tbody"></tbody>
            </table>
        </div>
    </div>

    <!-- SECTION D: VISUALIZATIONS TAB -->
    <div id="tab-visualizations" class="tab-content">
        <div class="charts-grid">
            <div class="chart-card">
                <div class="chart-title">Findings by Severity</div>
                <div id="chart-severity"></div>
            </div>
            <div class="chart-card">
                <div class="chart-title">Cryptographic Family Distribution</div>
                <div id="chart-families"></div>
            </div>
            <div class="chart-card">
                <div class="chart-title">PQC Quantum Exposure Breakdown</div>
                <div id="chart-pqc-exposure"></div>
            </div>
            <div class="chart-card">
                <div class="chart-title">PQC Migration Priority Breakdown</div>
                <div id="chart-pqc-priority"></div>
            </div>
            <div class="chart-card">
                <div class="chart-title">Top Files by Finding Count</div>
                <div id="chart-top-files"></div>
            </div>
            <div class="chart-card">
                <div class="chart-title">Resolved vs. Unresolved Observations</div>
                <div id="chart-resolution-ratio"></div>
            </div>
        </div>
    </div>

    <!-- SECTION E: UNRESOLVED OBSERVATIONS TAB -->
    <div id="tab-unresolved" class="tab-content">
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>Target API</th>
                        <th>Original Argument</th>
                        <th>File & Location</th>
                        <th>Resolution Status</th>
                        <th>Reason for Uncertainty</th>
                    </tr>
                </thead>
                <tbody id="unresolved-tbody"></tbody>
            </table>
        </div>
    </div>

    <!-- SECTION F: ANALYSIS LIMITATIONS TAB -->
    <div id="tab-limitations" class="tab-content">
        <div class="limitations-box">
            <h3>Static Cryptographic Analysis Scope & Boundaries</h3>
            <ul>
                <li><strong>Local AST Scope:</strong> Analysis inspects syntax expressions, compile-time constants, final local variables, and linear assignments within parsed compilation units.</li>
                <li><strong>No Dynamic Environment Resolution:</strong> Cryptographic algorithms configured via runtime configuration files (e.g. application.properties), remote service calls, or environment variables remain marked as <code>UNKNOWN</code>.</li>
                <li><strong>Absence of Runtime TLS Interception:</strong> TLS protocol versions reflect requested configurations (e.g. <code>SSLContext.getInstance("TLSv1.3")</code>) rather than actively negotiated socket sessions.</li>
                <li><strong>Non-Security Hashes:</strong> Algorithms such as MD5 or SHA-1 used exclusively for cache keys or file checksums trigger review findings; manual inspection should verify non-security usage.</li>
                <li><strong>Third-Party Cryptographic Wrappers:</strong> Non-JCA wrapper libraries (Bouncy Castle native APIs, Apache Commons Crypto) are tracked when binding to JCA providers.</li>
            </ul>
        </div>
    </div>

    <!-- SAFE EMBEDDED JSON DATA PAYLOAD -->
    <script id="security-payload" type="application/json">
{{SAFE_JSON_PAYLOAD}}
    </script>

    <!-- CLIENT LOGIC -->
    <script>
        const payload = JSON.parse(document.getElementById('security-payload').textContent);
        let currentFindingsSort = { key: 'severity', asc: false };

        function switchTab(tabId, btn) {
            document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
            document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
            const targetTab = document.getElementById(tabId);
            if (targetTab) { targetTab.classList.add('active'); }
            const activeBtn = btn || (typeof event !== 'undefined' && event && event.target ? event.target : null);
            if (activeBtn && activeBtn.classList) { activeBtn.classList.add('active'); }
            if (tabId === 'tab-visualizations') { renderCharts(); }
            if (tabId === 'tab-pqc') { renderPqc(); }
        }

        function renderFindings() {
            const query = document.getElementById('finding-search').value.toLowerCase().trim();
            const sevFilter = document.getElementById('severity-filter').value;
            const confFilter = document.getElementById('confidence-filter').value;

            const tbody = document.getElementById('findings-tbody');
            tbody.innerHTML = '';

            let filtered = payload.findings.filter(f => {
                if (sevFilter !== 'ALL' && f.severity !== sevFilter) return false;
                if (confFilter !== 'ALL' && f.confidence !== confFilter) return false;
                if (query) {
                    const match = f.ruleId.toLowerCase().includes(query)
                        || f.ruleName.toLowerCase().includes(query)
                        || f.message.toLowerCase().includes(query)
                        || (f.filePath && f.filePath.toLowerCase().includes(query));
                    if (!match) return false;
                }
                return true;
            });

            filtered.sort((a, b) => {
                let vA = a[currentFindingsSort.key] || '';
                let vB = b[currentFindingsSort.key] || '';
                if (currentFindingsSort.key === 'severity') {
                    const ranks = { 'CRITICAL': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1, 'INFO': 0 };
                    vA = ranks[a.severity] ?? 0;
                    vB = ranks[b.severity] ?? 0;
                }
                return currentFindingsSort.asc ? (vA > vB ? 1 : -1) : (vA < vB ? 1 : -1);
            });

            if (filtered.length === 0) {
                const tr = document.createElement('tr');
                const td = document.createElement('td');
                td.colSpan = 6;
                td.textContent = 'No security findings match the selected criteria.';
                td.style.textAlign = 'center';
                td.style.color = 'var(--text-muted)';
                tr.appendChild(td);
                tbody.appendChild(tr);
                return;
            }

            filtered.forEach((f, idx) => {
                const tr = document.createElement('tr');
                tr.className = 'clickable';
                tr.onclick = () => toggleDetail('f-detail-' + idx);

                const tdSev = document.createElement('td');
                const badge = document.createElement('span');
                badge.className = 'badge badge-' + f.severity;
                badge.textContent = f.severity;
                tdSev.appendChild(badge);

                const tdRule = document.createElement('td');
                tdRule.textContent = f.ruleId;
                tdRule.style.fontFamily = 'monospace';

                const tdName = document.createElement('td');
                tdName.textContent = f.ruleName;

                const tdLoc = document.createElement('td');
                tdLoc.textContent = (f.filePath || 'unknown') + (f.location ? ':' + f.location.startLine : '');

                const tdConf = document.createElement('td');
                tdConf.textContent = f.confidence;

                const tdCwe = document.createElement('td');
                tdCwe.textContent = f.cweId || '-';

                tr.appendChild(tdSev);
                tr.appendChild(tdRule);
                tr.appendChild(tdName);
                tr.appendChild(tdLoc);
                tr.appendChild(tdConf);
                tr.appendChild(tdCwe);
                tbody.appendChild(tr);

                // Expandable detail row
                const trDetail = document.createElement('tr');
                trDetail.id = 'f-detail-' + idx;
                trDetail.className = 'detail-row';
                const tdDetail = document.createElement('td');
                tdDetail.colSpan = 6;

                const box = document.createElement('div');
                box.className = 'detail-box';

                const msgSec = document.createElement('div');
                const msgTitle = document.createElement('div');
                msgTitle.className = 'detail-section-title';
                msgTitle.textContent = 'Description & Finding Message';
                const msgText = document.createElement('div');
                msgText.textContent = f.message;
                msgSec.appendChild(msgTitle);
                msgSec.appendChild(msgText);
                box.appendChild(msgSec);

                if (f.evidence) {
                    const evSec = document.createElement('div');
                    const evTitle = document.createElement('div');
                    evTitle.className = 'detail-section-title';
                    evTitle.textContent = 'Observed Evidence';
                    const evCode = document.createElement('div');
                    evCode.className = 'code-snippet';
                    evCode.textContent = f.evidence;
                    evSec.appendChild(evTitle);
                    evSec.appendChild(evCode);
                    box.appendChild(evSec);
                }

                if (f.remediation) {
                    const remSec = document.createElement('div');
                    const remTitle = document.createElement('div');
                    remTitle.className = 'detail-section-title';
                    remTitle.textContent = 'Remediation Guidance';
                    const remText = document.createElement('div');
                    remText.textContent = f.remediation;
                    remSec.appendChild(remTitle);
                    remSec.appendChild(remText);
                    box.appendChild(remSec);
                }

                tdDetail.appendChild(box);
                trDetail.appendChild(tdDetail);
                tbody.appendChild(trDetail);
            });
        }

        function toggleDetail(id) {
            const el = document.getElementById(id);
            if (el) el.classList.toggle('expanded');
        }

        function sortFindings(key) {
            if (currentFindingsSort.key === key) {
                currentFindingsSort.asc = !currentFindingsSort.asc;
            } else {
                currentFindingsSort.key = key;
                currentFindingsSort.asc = true;
            }
            renderFindings();
        }

        function renderInventory() {
            const query = document.getElementById('inventory-search').value.toLowerCase().trim();
            const primFilter = document.getElementById('primitive-filter').value;
            const tbody = document.getElementById('inventory-tbody');
            tbody.innerHTML = '';

            const filtered = payload.inventory.assets.filter(a => {
                if (primFilter !== 'ALL' && a.primitiveType !== primFilter) return false;
                if (query) {
                    const match = a.name.toLowerCase().includes(query) || a.algorithmFamily.toLowerCase().includes(query);
                    if (!match) return false;
                }
                return true;
            });

            if (filtered.length === 0) {
                const tr = document.createElement('tr');
                const td = document.createElement('td');
                td.colSpan = 6;
                td.textContent = 'No cryptographic assets match the selected criteria.';
                td.style.textAlign = 'center';
                td.style.color = 'var(--text-muted)';
                tr.appendChild(td);
                tbody.appendChild(tr);
                return;
            }

            filtered.forEach((a, idx) => {
                const tr = document.createElement('tr');
                tr.className = 'clickable';
                tr.onclick = () => toggleDetail('inv-detail-' + idx);

                const tdName = document.createElement('td');
                tdName.textContent = a.name;
                tdName.style.fontWeight = '600';

                const tdFam = document.createElement('td');
                tdFam.textContent = a.algorithmFamily;

                const tdPrim = document.createElement('td');
                tdPrim.textContent = a.primitiveType.replace('_', ' ');

                const tdMode = document.createElement('td');
                tdMode.textContent = (a.mode || '-') + ' / ' + (a.padding || '-');

                const tdCount = document.createElement('td');
                tdCount.textContent = a.usages.length + ' calls';

                const tdStatus = document.createElement('td');
                const badge = document.createElement('span');
                badge.className = 'badge ' + (a.isResolved ? 'badge-clean' : 'badge-unknown');
                badge.textContent = a.isResolved ? 'Resolved' : 'Unresolved';
                tdStatus.appendChild(badge);

                tr.appendChild(tdName);
                tr.appendChild(tdFam);
                tr.appendChild(tdPrim);
                tr.appendChild(tdMode);
                tr.appendChild(tdCount);
                tr.appendChild(tdStatus);
                tbody.appendChild(tr);

                // Usages detail row
                const trDetail = document.createElement('tr');
                trDetail.id = 'inv-detail-' + idx;
                trDetail.className = 'detail-row';
                const tdDetail = document.createElement('td');
                tdDetail.colSpan = 6;
                const box = document.createElement('div');
                box.className = 'detail-box';

                const title = document.createElement('div');
                title.className = 'detail-section-title';
                title.textContent = 'Observed Source Call Sites (' + a.usages.length + ')';
                box.appendChild(title);

                a.usages.forEach(u => {
                    const uDiv = document.createElement('div');
                    uDiv.style.fontSize = '12px';
                    uDiv.textContent = u.filePath + ':' + u.location.startLine + ' - ' + u.apiOwner + '.' + u.methodName + '(' + (u.originalArgument || '') + ')';
                    box.appendChild(uDiv);
                });

                tdDetail.appendChild(box);
                trDetail.appendChild(tdDetail);
                tbody.appendChild(trDetail);
            });
        }

        function renderUnresolved() {
            const tbody = document.getElementById('unresolved-tbody');
            tbody.innerHTML = '';

            const unresolvedList = [];
            payload.inventory.assets.forEach(a => {
                if (!a.isResolved) {
                    a.usages.forEach(u => unresolvedList.push(u));
                }
            });

            if (unresolvedList.length === 0) {
                const tr = document.createElement('tr');
                const td = document.createElement('td');
                td.colSpan = 5;
                td.textContent = 'No unresolved or ambiguous cryptographic expressions detected.';
                td.style.textAlign = 'center';
                td.style.color = 'var(--text-muted)';
                tr.appendChild(td);
                tbody.appendChild(tr);
                return;
            }

            unresolvedList.forEach(u => {
                const tr = document.createElement('tr');
                const tdApi = document.createElement('td');
                tdApi.textContent = u.apiOwner + '.' + u.methodName;

                const tdArg = document.createElement('td');
                tdArg.className = 'code-snippet';
                tdArg.textContent = u.originalArgument || '<empty>';

                const tdLoc = document.createElement('td');
                tdLoc.textContent = u.filePath + ':' + u.location.startLine;

                const tdStat = document.createElement('td');
                const badge = document.createElement('span');
                badge.className = 'badge badge-unknown';
                badge.textContent = u.argumentResolution.status;
                tdStat.appendChild(badge);

                const tdExp = document.createElement('td');
                tdExp.textContent = u.argumentResolution.explanation || '-';

                tr.appendChild(tdApi);
                tr.appendChild(tdArg);
                tr.appendChild(tdLoc);
                tr.appendChild(tdStat);
                tr.appendChild(tdExp);
                tbody.appendChild(tr);
            });
        }

        function renderCharts() {
            renderSeverityChart();
            renderFamilyChart();
            renderTopFilesChart();
            renderResolutionRatioChart();
            renderPqcExposureChart();
            renderPqcPriorityChart();
        }

        function renderPqcExposureChart() {
            const container = document.getElementById('chart-pqc-exposure');
            if (!container) return;
            container.innerHTML = '';
            if (!payload.pqc || !payload.pqc.summary || !payload.pqc.summary.exposureBreakdown) {
                container.textContent = 'No PQC assessment data available.';
                container.style.color = 'var(--text-muted)';
                return;
            }
            const data = [
                { label: 'Quantum Vulnerable', val: payload.pqc.summary.quantumVulnerable || 0, color: 'var(--critical)' },
                { label: 'Quantum Impacted', val: payload.pqc.summary.quantumImpacted || 0, color: 'var(--medium)' },
                { label: 'Quantum Safe (PQC)', val: payload.pqc.summary.quantumSafe || 0, color: 'var(--clean)' },
                { label: 'Unknown Exposure', val: payload.pqc.summary.unknown || 0, color: 'var(--unknown)' }
            ];
            const max = Math.max(...data.map(d => d.val), 1);
            data.forEach(item => {
                const row = document.createElement('div');
                row.className = 'bar-chart-row';
                const label = document.createElement('div');
                label.className = 'bar-chart-label';
                label.textContent = item.label;
                const track = document.createElement('div');
                track.className = 'bar-chart-track';
                const fill = document.createElement('div');
                fill.className = 'bar-chart-fill';
                fill.style.width = ((item.val / max) * 100) + '%';
                fill.style.backgroundColor = item.color;
                track.appendChild(fill);
                const valDiv = document.createElement('div');
                valDiv.className = 'bar-chart-val';
                valDiv.textContent = item.val;
                row.appendChild(label);
                row.appendChild(track);
                row.appendChild(valDiv);
                container.appendChild(row);
            });
        }

        function renderPqcPriorityChart() {
            const container = document.getElementById('chart-pqc-priority');
            if (!container) return;
            container.innerHTML = '';
            if (!payload.pqc || !payload.pqc.summary) {
                container.textContent = 'No PQC assessment data available.';
                container.style.color = 'var(--text-muted)';
                return;
            }
            const data = [
                { label: 'Critical Priority', val: payload.pqc.summary.criticalPriority || 0, color: 'var(--critical)' },
                { label: 'High Priority', val: payload.pqc.summary.highPriority || 0, color: 'var(--high)' },
                { label: 'Medium Priority', val: payload.pqc.summary.mediumPriority || 0, color: 'var(--medium)' },
                { label: 'Low / Info Priority', val: payload.pqc.summary.lowPriority || 0, color: 'var(--low)' }
            ];
            const max = Math.max(...data.map(d => d.val), 1);
            data.forEach(item => {
                const row = document.createElement('div');
                row.className = 'bar-chart-row';
                const label = document.createElement('div');
                label.className = 'bar-chart-label';
                label.textContent = item.label;
                const track = document.createElement('div');
                track.className = 'bar-chart-track';
                const fill = document.createElement('div');
                fill.className = 'bar-chart-fill';
                fill.style.width = ((item.val / max) * 100) + '%';
                fill.style.backgroundColor = item.color;
                track.appendChild(fill);
                const valDiv = document.createElement('div');
                valDiv.className = 'bar-chart-val';
                valDiv.textContent = item.val;
                row.appendChild(label);
                row.appendChild(track);
                row.appendChild(valDiv);
                container.appendChild(row);
            });
        }

        function renderSeverityChart() {
            const container = document.getElementById('chart-severity');
            container.innerHTML = '';
            const counts = { 'CRITICAL': 0, 'HIGH': 0, 'MEDIUM': 0, 'LOW': 0, 'INFO': 0 };
            payload.findings.forEach(f => { if (counts[f.severity] !== undefined) counts[f.severity]++; });

            const colors = { 'CRITICAL': 'var(--critical)', 'HIGH': 'var(--high)', 'MEDIUM': 'var(--medium)', 'LOW': 'var(--low)', 'INFO': 'var(--text-muted)' };
            const max = Math.max(...Object.values(counts), 1);

            Object.entries(counts).forEach(([sev, val]) => {
                const row = document.createElement('div');
                row.className = 'bar-chart-row';

                const label = document.createElement('div');
                label.className = 'bar-chart-label';
                label.textContent = sev;

                const track = document.createElement('div');
                track.className = 'bar-chart-track';

                const fill = document.createElement('div');
                fill.className = 'bar-chart-fill';
                fill.style.width = ((val / max) * 100) + '%';
                fill.style.backgroundColor = colors[sev];
                track.appendChild(fill);

                const valDiv = document.createElement('div');
                valDiv.className = 'bar-chart-val';
                valDiv.textContent = val;

                row.appendChild(label);
                row.appendChild(track);
                row.appendChild(valDiv);
                container.appendChild(row);
            });
        }

        function renderFamilyChart() {
            const container = document.getElementById('chart-families');
            container.innerHTML = '';
            const families = payload.inventory.summary.familyCounts || {};
            const sorted = Object.entries(families).sort((a, b) => b[1] - a[1]);
            const max = sorted.length > 0 ? sorted[0][1] : 1;

            if (sorted.length === 0) {
                container.textContent = 'No cryptographic algorithms observed.';
                container.style.color = 'var(--text-muted)';
                return;
            }

            sorted.forEach(([fam, val]) => {
                const row = document.createElement('div');
                row.className = 'bar-chart-row';

                const label = document.createElement('div');
                label.className = 'bar-chart-label';
                label.textContent = fam;
                label.title = fam;

                const track = document.createElement('div');
                track.className = 'bar-chart-track';

                const fill = document.createElement('div');
                fill.className = 'bar-chart-fill';
                fill.style.width = ((val / max) * 100) + '%';
                fill.style.backgroundColor = 'var(--accent)';
                track.appendChild(fill);

                const valDiv = document.createElement('div');
                valDiv.className = 'bar-chart-val';
                valDiv.textContent = val;

                row.appendChild(label);
                row.appendChild(track);
                row.appendChild(valDiv);
                container.appendChild(row);
            });
        }

        function renderTopFilesChart() {
            const container = document.getElementById('chart-top-files');
            container.innerHTML = '';
            const fileCounts = {};
            payload.findings.forEach(f => {
                if (f.filePath) {
                    fileCounts[f.filePath] = (fileCounts[f.filePath] || 0) + 1;
                }
            });
            const sorted = Object.entries(fileCounts).sort((a, b) => b[1] - a[1]).slice(0, 5);
            const max = sorted.length > 0 ? sorted[0][1] : 1;

            if (sorted.length === 0) {
                container.textContent = 'No findings across repository files.';
                container.style.color = 'var(--clean)';
                return;
            }

            sorted.forEach(([file, val]) => {
                const row = document.createElement('div');
                row.className = 'bar-chart-row';

                const label = document.createElement('div');
                label.className = 'bar-chart-label';
                label.title = file;
                label.textContent = file;

                const track = document.createElement('div');
                track.className = 'bar-chart-track';

                const fill = document.createElement('div');
                fill.className = 'bar-chart-fill';
                fill.style.width = ((val / max) * 100) + '%';
                fill.style.backgroundColor = 'var(--high)';
                track.appendChild(fill);

                const valDiv = document.createElement('div');
                valDiv.className = 'bar-chart-val';
                valDiv.textContent = val;

                row.appendChild(label);
                row.appendChild(track);
                row.appendChild(valDiv);
                container.appendChild(row);
            });
        }

        function renderResolutionRatioChart() {
            const container = document.getElementById('chart-resolution-ratio');
            container.innerHTML = '';
            const total = payload.inventory.summary.totalObservations || 0;
            const unres = payload.inventory.summary.unresolvedObservations || 0;
            const res = Math.max(0, total - unres);
            const max = Math.max(total, 1);

            const rowRes = document.createElement('div');
            rowRes.className = 'bar-chart-row';
            const labelRes = document.createElement('div');
            labelRes.className = 'bar-chart-label';
            labelRes.textContent = 'Resolved Assets';
            const trackRes = document.createElement('div');
            trackRes.className = 'bar-chart-track';
            const fillRes = document.createElement('div');
            fillRes.className = 'bar-chart-fill';
            fillRes.style.width = ((res / max) * 100) + '%';
            fillRes.style.backgroundColor = 'var(--clean)';
            trackRes.appendChild(fillRes);
            const valRes = document.createElement('div');
            valRes.className = 'bar-chart-val';
            valRes.textContent = res;
            rowRes.appendChild(labelRes);
            rowRes.appendChild(trackRes);
            rowRes.appendChild(valRes);
            container.appendChild(rowRes);

            const rowUnres = document.createElement('div');
            rowUnres.className = 'bar-chart-row';
            const labelUnres = document.createElement('div');
            labelUnres.className = 'bar-chart-label';
            labelUnres.textContent = 'Unresolved Usages';
            const trackUnres = document.createElement('div');
            trackUnres.className = 'bar-chart-track';
            const fillUnres = document.createElement('div');
            fillUnres.className = 'bar-chart-fill';
            fillUnres.style.width = ((unres / max) * 100) + '%';
            fillUnres.style.backgroundColor = 'var(--medium)';
            trackUnres.appendChild(fillUnres);
            const valUnres = document.createElement('div');
            valUnres.className = 'bar-chart-val';
            valUnres.textContent = unres;
            rowUnres.appendChild(labelUnres);
            rowUnres.appendChild(trackUnres);
            rowUnres.appendChild(valUnres);
            container.appendChild(rowUnres);
        }

        let currentPqcSort = { key: 'priority', asc: false };

        function sortPqc(key) {
            if (currentPqcSort.key === key) {
                currentPqcSort.asc = !currentPqcSort.asc;
            } else {
                currentPqcSort.key = key;
                currentPqcSort.asc = true;
            }
            renderPqc();
        }

        function renderPqc() {
            if (!payload.pqc || !payload.pqc.assessments) return;
            const query = (document.getElementById('pqc-search') ? document.getElementById('pqc-search').value : '').toLowerCase().trim();
            const catFilter = document.getElementById('pqc-category-filter') ? document.getElementById('pqc-category-filter').value : 'ALL';
            const expFilter = document.getElementById('pqc-exposure-filter') ? document.getElementById('pqc-exposure-filter').value : 'ALL';
            const prioFilter = document.getElementById('pqc-priority-filter') ? document.getElementById('pqc-priority-filter').value : 'ALL';

            const tbody = document.getElementById('pqc-tbody');
            if (!tbody) return;
            tbody.innerHTML = '';

            let filtered = payload.pqc.assessments.filter(a => {
                if (catFilter !== 'ALL' && a.category !== catFilter) return false;
                if (expFilter !== 'ALL' && a.exposureLevel !== expFilter) return false;
                if (prioFilter !== 'ALL' && a.priority !== prioFilter) return false;
                if (query) {
                    const match = a.name.toLowerCase().includes(query)
                        || a.algorithmFamily.toLowerCase().includes(query)
                        || a.purpose.toLowerCase().includes(query)
                        || (a.categoryDisplayName && a.categoryDisplayName.toLowerCase().includes(query))
                        || (a.recommendation && a.recommendation.targetStandard.toLowerCase().includes(query))
                        || (a.blastRadius && a.blastRadius.directDependentClasses && a.blastRadius.directDependentClasses.some(c => c.toLowerCase().includes(query)))
                        || (a.blastRadius && a.blastRadius.transitiveDependentClasses && a.blastRadius.transitiveDependentClasses.some(c => c.toLowerCase().includes(query)));
                    if (!match) return false;
                }
                return true;
            });

            filtered.sort((a, b) => {
                let vA = a[currentPqcSort.key] || '';
                let vB = b[currentPqcSort.key] || '';
                if (currentPqcSort.key === 'priority') {
                    const ranks = { 'CRITICAL': 5, 'HIGH': 4, 'REVIEW_REQUIRED': 3, 'MEDIUM': 2, 'LOW': 1, 'INFO': 0, 'PRIORITY_UNDETERMINED': 0 };
                    vA = ranks[a.priority] ?? 0;
                    vB = ranks[b.priority] ?? 0;
                } else if (currentPqcSort.key === 'exposureLevel') {
                    const ranks = { 'QUANTUM_VULNERABLE': 4, 'CLASSICALLY_WEAK': 3, 'QUANTUM_IMPACTED': 2, 'UNKNOWN': 1, 'QUANTUM_SAFE': 0 };
                    vA = ranks[a.exposureLevel] ?? 0;
                    vB = ranks[b.exposureLevel] ?? 0;
                }
                return currentPqcSort.asc ? (vA > vB ? 1 : -1) : (vA < vB ? 1 : -1);
            });

            if (filtered.length === 0) {
                const tr = document.createElement('tr');
                const td = document.createElement('td');
                td.colSpan = 6;
                td.textContent = 'No cryptographic assets match the selected PQC criteria.';
                td.style.textAlign = 'center';
                td.style.color = 'var(--text-muted)';
                tr.appendChild(td);
                tbody.appendChild(tr);
                return;
            }

            filtered.forEach((a, idx) => {
                const tr = document.createElement('tr');
                tr.className = 'clickable';
                tr.onclick = () => toggleDetail('pqc-detail-' + idx);

                const tdName = document.createElement('td');
                const nameDiv = document.createElement('strong');
                nameDiv.textContent = a.name;
                const catDiv = document.createElement('div');
                catDiv.style.fontSize = '11px';
                catDiv.style.fontWeight = 'bold';
                catDiv.style.color = a.category === 'PQC_MIGRATION_CANDIDATE' ? 'var(--critical)' :
                                     (a.category === 'CLASSICAL_REMEDIATION' ? '#d946ef' :
                                     (a.category === 'QUANTUM_CONSIDERATION' ? 'var(--medium)' : 'var(--unknown)'));
                catDiv.textContent = a.categoryDisplayName || a.category;
                const famDiv = document.createElement('div');
                famDiv.style.color = 'var(--text-muted)';
                famDiv.style.fontSize = '11px';
                famDiv.textContent = 'Family: ' + a.algorithmFamily + ' | ' + a.primitiveType;
                tdName.appendChild(nameDiv);
                tdName.appendChild(catDiv);
                tdName.appendChild(famDiv);

                const tdExp = document.createElement('td');
                const badgeExp = document.createElement('span');
                badgeExp.className = 'badge badge-' + a.exposureLevel;
                badgeExp.textContent = a.exposureLevel.replace(/_/g, ' ');
                tdExp.appendChild(badgeExp);

                const tdPurp = document.createElement('td');
                tdPurp.textContent = a.purpose.replace(/_/g, ' ');

                const tdPrio = document.createElement('td');
                const badgePrio = document.createElement('span');
                badgePrio.className = 'badge badge-' + a.priority;
                badgePrio.textContent = a.priority;
                tdPrio.appendChild(badgePrio);

                const tdRec = document.createElement('td');
                tdRec.textContent = a.recommendation ? a.recommendation.targetStandard : '-';

                const tdBr = document.createElement('td');
                const br = a.blastRadius || { directDependentClasses: [], transitiveOnlyDependentClasses: [], maxCallDepth: 0 };
                const directCount = br.totalDirectDependents ?? (br.directDependentClasses ? br.directDependentClasses.length : 0);
                const transOnlyCount = br.totalTransitiveOnlyDependents ?? (br.transitiveOnlyDependentClasses ? br.transitiveOnlyDependentClasses.length : 0);
                const utilMarker = br.isSharedUtilityUsage ? ' *' : '';
                tdBr.textContent = directCount + ' dir / ' + transOnlyCount + ' trans-only (depth ' + br.maxCallDepth + ')' + utilMarker;

                tr.appendChild(tdName);
                tr.appendChild(tdExp);
                tr.appendChild(tdPurp);
                tr.appendChild(tdPrio);
                tr.appendChild(tdRec);
                tr.appendChild(tdBr);
                tbody.appendChild(tr);

                // Expandable Detail Row
                const trDetail = document.createElement('tr');
                trDetail.id = 'pqc-detail-' + idx;
                trDetail.className = 'detail-row';
                const tdDetail = document.createElement('td');
                tdDetail.colSpan = 6;

                const detailBox = document.createElement('div');
                detailBox.className = 'detail-box';

                // Assessment Category & Strategic Scope
                const catTitle = document.createElement('div');
                catTitle.className = 'detail-section-title';
                catTitle.textContent = 'Assessment Category & Strategic Scope';
                const catContent = document.createElement('div');
                catContent.style.color = 'var(--text-main)';
                catContent.textContent = (a.categoryDisplayName || a.category) + (a.category === 'QUANTUM_CONSIDERATION' ? ' (Modern cryptographic primitive; NOT a mandatory PQC replacement candidate. Maintain modern baseline or consider key length hardening).' : '');
                detailBox.appendChild(catTitle);
                detailBox.appendChild(catContent);

                // Organizational Migration Review Status
                const orgTitle = document.createElement('div');
                orgTitle.className = 'detail-section-title';
                orgTitle.style.marginTop = '6px';
                orgTitle.textContent = 'Organizational Migration Review Status';
                const orgContent = document.createElement('div');
                orgContent.style.fontSize = '12px';
                orgContent.style.color = 'var(--text-muted)';
                orgContent.textContent = 'Review Status: ' + a.priority + '. Migration timeline and business criticality must be determined by organizational data confidentiality horizons, forward-secrecy requirements, and threat model.';
                detailBox.appendChild(orgTitle);
                detailBox.appendChild(orgContent);

                // Threat Assessment
                const threatTitle = document.createElement('div');
                threatTitle.className = 'detail-section-title';
                threatTitle.textContent = 'Quantum Threat Assessment';
                const threatContent = document.createElement('div');
                threatContent.textContent = a.threatSummary;
                threatContent.style.color = 'var(--text-main)';
                detailBox.appendChild(threatTitle);
                detailBox.appendChild(threatContent);

                // NIST Guidance
                if (a.recommendation) {
                    const recTitle = document.createElement('div');
                    recTitle.className = 'detail-section-title';
                    recTitle.textContent = 'NIST Post-Quantum Migration Recommendation (' + a.recommendation.standardFamily + ')';
                    const recContent = document.createElement('div');
                    recContent.textContent = a.recommendation.nistGuidanceRationale;

                    const stratTitle = document.createElement('div');
                    stratTitle.className = 'detail-section-title';
                    stratTitle.style.marginTop = '6px';
                    stratTitle.textContent = 'Transition Strategy & Standards Reference';
                    const stratContent = document.createElement('div');
                    stratContent.textContent = a.recommendation.transitionStrategy + ' [' + a.recommendation.fipsReference + ']';
                    stratContent.style.color = '#38bdf8';

                    detailBox.appendChild(recTitle);
                    detailBox.appendChild(recContent);
                    detailBox.appendChild(stratTitle);
                    detailBox.appendChild(stratContent);
                }

                // Blast Radius Callers & Semantics Note
                if (br.reachabilitySemanticsNote) {
                    const noteTitle = document.createElement('div');
                    noteTitle.className = 'detail-section-title';
                    noteTitle.style.marginTop = '6px';
                    noteTitle.textContent = 'Reachability Semantics:';
                    const noteBox = document.createElement('div');
                    noteBox.style.fontSize = '12px';
                    noteBox.style.color = br.isSharedUtilityUsage ? 'var(--medium)' : 'var(--text-muted)';
                    noteBox.textContent = br.reachabilitySemanticsNote;
                    detailBox.appendChild(noteTitle);
                    detailBox.appendChild(noteBox);
                }

                if (br.directDependentClasses && br.directDependentClasses.length > 0) {
                    const brTitle = document.createElement('div');
                    brTitle.className = 'detail-section-title';
                    brTitle.style.marginTop = '6px';
                    brTitle.textContent = 'Direct Caller Classes (depth = 1) [' + br.directDependentClasses.length + ']:';
                    const brBox = document.createElement('div');
                    brBox.className = 'code-snippet';
                    brBox.textContent = br.directDependentClasses.join(', ');
                    detailBox.appendChild(brTitle);
                    detailBox.appendChild(brBox);
                }

                const transList = br.transitiveOnlyDependentClasses || br.transitiveDependentClasses || [];
                if (transList.length > 0) {
                    const transTitle = document.createElement('div');
                    transTitle.className = 'detail-section-title';
                    transTitle.style.marginTop = '6px';
                    transTitle.textContent = 'Transitive-Only Caller Classes (depth >= 2) [' + transList.length + ']:';
                    const transBox = document.createElement('div');
                    transBox.className = 'code-snippet';
                    transBox.textContent = transList.join(', ');
                    detailBox.appendChild(transTitle);
                    detailBox.appendChild(transBox);
                }

                // Observed Usages
                if (a.usages && a.usages.length > 0) {
                    const usTitle = document.createElement('div');
                    usTitle.className = 'detail-section-title';
                    usTitle.style.marginTop = '6px';
                    usTitle.textContent = 'Observed Code Locations (' + a.usages.length + '):';
                    const usList = document.createElement('div');
                    a.usages.forEach(u => {
                        const usItem = document.createElement('div');
                        usItem.style.fontSize = '12px';
                        usItem.style.marginBottom = '2px';
                        usItem.textContent = u.filePath + ':' + u.line + ' [' + u.apiOwner + '.' + u.methodName + '("' + u.originalArgument + '")]';
                        usList.appendChild(usItem);
                    });
                    detailBox.appendChild(usTitle);
                    detailBox.appendChild(usList);
                }

                tdDetail.appendChild(detailBox);
                trDetail.appendChild(tdDetail);
                tbody.appendChild(trDetail);
            });
        }

        // Initial Load
        renderFindings();
        renderInventory();
        renderPqc();
        renderUnresolved();
    </script>
</body>
</html>
""";

        return template
                .replace("{{SCANNER_VERSION}}", escapeHtml(payload.scannerVersion()))
                .replace("{{REPOSITORY_NAME}}", escapeHtml(payload.repositoryName()))
                .replace("{{FORMATTED_TIMESTAMP}}", escapeHtml(formattedTimestamp))
                .replace("{{FILES_SCANNED}}", String.valueOf(payload.analysisResult().filesScanned()))
                .replace("{{FINDINGS_COLOR}}", totalFindings > 0 ? "var(--critical)" : "var(--clean)")
                .replace("{{TOTAL_FINDINGS}}", String.valueOf(totalFindings))
                .replace("{{HIGH_COUNT}}", String.valueOf(highCount))
                .replace("{{MED_COUNT}}", String.valueOf(medCount))
                .replace("{{TOTAL_OBSERVATIONS}}", String.valueOf(inv.summary().totalObservations()))
                .replace("{{UNIQUE_ASSETS}}", String.valueOf(inv.summary().uniqueAssets()))
                .replace("{{UNRESOLVED_COLOR}}", inv.summary().unresolvedObservations() > 0 ? "var(--medium)" : "var(--text-muted)")
                .replace("{{UNRESOLVED_COUNT}}", String.valueOf(inv.summary().unresolvedObservations()))
                .replace("{{INVENTORY_ASSETS_COUNT}}", String.valueOf(inv.assets().size()))
                .replace("{{PQC_VULNERABLE_COUNT}}", String.valueOf(pqcVulnerableCount))
                .replace("{{PQC_IMPACTED_COUNT}}", String.valueOf(pqcImpactedCount))
                .replace("{{PQC_CLASSICALLY_WEAK_COUNT}}", String.valueOf(pqcClassicallyWeakCount))
                .replace("{{PQC_SAFE_COUNT}}", String.valueOf(pqcSafeCount))
                .replace("{{PQC_AFFECTED_CLASSES_COUNT}}", String.valueOf(pqcAffectedClassesCount))
                .replace("{{SAFE_JSON_PAYLOAD}}", safeJsonPayload);
    }

    private String serializeSafePayloadJson(SecurityReportPayload payload) {
        Path repoRoot = payload.repositoryPath();
        ObjectNode root = mapper.createObjectNode();
        root.put("repositoryName", payload.repositoryName());
        root.put("repositoryPath", relativizePath(payload.repositoryPath(), repoRoot));
        root.put("scannerVersion", payload.scannerVersion());
        root.put("scanTimestamp", payload.scanTimestamp().toString());

        // Findings
        ArrayNode findingsArr = root.putArray("findings");
        for (Finding f : payload.analysisResult().findings()) {
            ObjectNode fn = findingsArr.addObject();
            fn.put("ruleId", f.ruleId());
            fn.put("ruleName", f.ruleName());
            fn.put("category", f.category().name());
            fn.put("severity", f.severity().name());
            fn.put("message", f.message());
            f.filePath().ifPresent(p -> fn.put("filePath", relativizePath(p, repoRoot)));
            f.location().ifPresent(loc -> {
                ObjectNode locNode = fn.putObject("location");
                locNode.put("startLine", loc.startLine());
                locNode.put("startColumn", loc.startColumn());
                locNode.put("endLine", loc.endLine());
                locNode.put("endColumn", loc.endColumn());
            });
            fn.put("evidence", f.evidence());
            fn.put("remediation", f.remediation());
            f.cweId().ifPresent(cwe -> fn.put("cweId", cwe));
            fn.put("confidence", f.confidence().name());
        }

        // Inventory
        ObjectNode invNode = root.putObject("inventory");
        ObjectNode summaryNode = invNode.putObject("summary");
        summaryNode.put("totalObservations", payload.cryptoInventory().summary().totalObservations());
        summaryNode.put("uniqueAssets", payload.cryptoInventory().summary().uniqueAssets());
        summaryNode.put("resolvedAssets", payload.cryptoInventory().summary().resolvedAssets());
        summaryNode.put("unresolvedObservations", payload.cryptoInventory().summary().unresolvedObservations());
        summaryNode.put("filesWithCrypto", payload.cryptoInventory().summary().filesWithCrypto());

        ObjectNode familyCounts = summaryNode.putObject("familyCounts");
        payload.cryptoInventory().summary().familyCounts().forEach(familyCounts::put);

        ArrayNode assetsArr = invNode.putArray("assets");
        for (CryptoAsset a : payload.cryptoInventory().assets()) {
            ObjectNode an = assetsArr.addObject();
            an.put("assetId", a.assetId());
            an.put("name", a.name());
            an.put("algorithmFamily", a.algorithmFamily());
            an.put("primitiveType", a.primitiveType().name());
            a.mode().ifPresent(m -> an.put("mode", m));
            a.padding().ifPresent(p -> an.put("padding", p));
            an.put("isResolved", a.isResolved());
            an.put("confidence", a.confidence().name());

            ArrayNode usagesArr = an.putArray("usages");
            for (CryptoAssetUsage u : a.usages()) {
                ObjectNode un = usagesArr.addObject();
                un.put("usageId", u.usageId());
                un.put("filePath", relativizePath(u.filePath(), repoRoot));
                ObjectNode locNode = un.putObject("location");
                locNode.put("startLine", u.location().startLine());
                locNode.put("startColumn", u.location().startColumn());
                un.put("apiOwner", u.apiOwner().name());
                un.put("methodName", u.methodName());
                un.put("originalArgument", u.originalArgument());
                ObjectNode resNode = un.putObject("argumentResolution");
                resNode.put("status", u.argumentResolution().status().name());
                resNode.put("explanation", u.argumentResolution().explanation());
            }
        }

        // PQC Assessment
        if (payload.pqcReport().isPresent()) {
            PqcAssessmentReport pqc = payload.pqcReport().get();
            ObjectNode pqcNode = root.putObject("pqc");
            ObjectNode pqcSummary = pqcNode.putObject("summary");
            pqcSummary.put("totalAssets", pqc.totalAssetsAnalyzed());
            pqcSummary.put("quantumVulnerable", pqc.quantumVulnerableCount());
            pqcSummary.put("quantumImpacted", pqc.quantumImpactedCount());
            pqcSummary.put("classicallyWeak", pqc.classicallyWeakCount());
            pqcSummary.put("quantumSafe", pqc.quantumSafeCount());
            pqcSummary.put("unknown", pqc.unknownExposureCount());
            pqcSummary.put("pqcMigrationCandidates", pqc.pqcMigrationCandidatesCount());
            pqcSummary.put("classicalRemediation", pqc.classicalRemediationCount());
            pqcSummary.put("quantumConsiderations", pqc.quantumConsiderationsCount());
            pqcSummary.put("unknownOrUnresolved", pqc.unknownOrUnresolvedCount());
            pqcSummary.put("criticalPriority", pqc.criticalPriorityCount());
            pqcSummary.put("highPriority", pqc.highPriorityCount());
            pqcSummary.put("mediumPriority", pqc.mediumPriorityCount());
            pqcSummary.put("lowPriority", pqc.lowPriorityCount());
            pqcSummary.put("reviewRequiredPriority", pqc.reviewRequiredPriorityCount());
            pqcSummary.put("undeterminedPriority", pqc.undeterminedPriorityCount());
            pqcSummary.put("totalAffectedClasses", pqc.totalAffectedClassesInBlastRadius());

            ObjectNode expMap = pqcSummary.putObject("exposureBreakdown");
            pqc.exposureBreakdown().forEach((k, v) -> expMap.put(k.name(), v));

            ObjectNode purpMap = pqcSummary.putObject("purposeBreakdown");
            pqc.purposeBreakdown().forEach((k, v) -> purpMap.put(k.name(), v));

            ArrayNode pqcAssessments = pqcNode.putArray("assessments");
            for (PqcAssetAssessment a : pqc.assessments()) {
                ObjectNode an = pqcAssessments.addObject();
                an.put("assetId", a.asset().assetId());
                an.put("name", a.asset().name());
                an.put("algorithmFamily", a.asset().algorithmFamily());
                an.put("primitiveType", a.asset().primitiveType().name());
                an.put("category", a.category().name());
                an.put("categoryDisplayName", a.category().displayName());
                an.put("exposureLevel", a.exposureLevel().name());
                an.put("purpose", a.purpose().name());
                an.put("priority", a.priority().name());
                an.put("threatSummary", a.quantumThreatSummary());

                ObjectNode recNode = an.putObject("recommendation");
                recNode.put("targetStandard", a.recommendation().targetStandard());
                recNode.put("standardFamily", a.recommendation().standardFamily());
                recNode.put("transitionStrategy", a.recommendation().transitionStrategy());
                recNode.put("nistGuidanceRationale", a.recommendation().rationale());
                recNode.put("fipsReference", a.recommendation().standardFamily());

                ObjectNode brNode = an.putObject("blastRadius");
                ArrayNode directArr = brNode.putArray("directDependentClasses");
                a.blastRadius().directDependentClasses().forEach(directArr::add);

                ArrayNode transArr = brNode.putArray("transitiveDependentClasses");
                a.blastRadius().transitiveDependentClasses().forEach(transArr::add);

                ArrayNode transOnlyArr = brNode.putArray("transitiveOnlyDependentClasses");
                a.blastRadius().transitiveOnlyDependentClasses().forEach(transOnlyArr::add);

                brNode.put("totalDirectDependents", a.blastRadius().totalDirectDependents());
                brNode.put("totalTransitiveOnlyDependents", a.blastRadius().totalTransitiveOnlyDependents());
                brNode.put("totalReachableClasses", a.blastRadius().totalReachableClasses());
                brNode.put("maxCallDepth", a.blastRadius().maxCallDepth());
                brNode.put("cumulativeRiskScore", a.blastRadius().cumulativeRiskScore());
                brNode.put("isSharedUtilityUsage", a.blastRadius().isSharedUtilityUsage());
                brNode.put("reachabilitySemanticsNote", a.blastRadius().reachabilitySemanticsNote());

                ArrayNode cusArr = brNode.putArray("affectedCompilationUnits");
                a.blastRadius().affectedCompilationUnits().forEach(cusArr::add);

                ArrayNode usagesArr = an.putArray("usages");
                for (CryptoAssetUsage u : a.asset().usages()) {
                    ObjectNode un = usagesArr.addObject();
                    un.put("filePath", relativizePath(u.filePath(), repoRoot));
                    un.put("line", u.location().startLine());
                    un.put("column", u.location().startColumn());
                    un.put("apiOwner", u.apiOwner().name());
                    un.put("methodName", u.methodName());
                    un.put("originalArgument", u.originalArgument());
                    un.put("status", u.argumentResolution().status().name());
                }
            }
        }

        try {
            String rawJson = mapper.writeValueAsString(root);
            // Escape '<', '>', '&' to prevent embedded script termination or injection
            return rawJson.replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SecurityReportPayload JSON", e);
        }
    }

    private static String relativizePath(Path path, Path repoRoot) {
        if (path == null) return "unknown";
        if (repoRoot == null) return path.toString().replace('\\', '/');
        try {
            Path normRepo = repoRoot.toAbsolutePath().normalize();
            Path normPath = path.toAbsolutePath().normalize();
            if (normPath.equals(normRepo)) {
                return ".";
            }
            if (normPath.startsWith(normRepo)) {
                return normRepo.relativize(normPath).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return path.toString().replace('\\', '/');
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
