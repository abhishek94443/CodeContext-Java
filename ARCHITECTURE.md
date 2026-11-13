# CodeContext-Java Architecture

> **Deterministic Codebase Intelligence, Graph Analytics & Change-Safety for Java**  
> Maintained by: Abhishek Dwivedi ([abhishekdwivedi94443@gmail.com](mailto:abhishekdwivedi94443@gmail.com))  
> Repository: [https://github.com/abhishek94443/CodeContext-Java](https://github.com/abhishek94443/CodeContext-Java)

CodeContext-Java is a deterministic codebase intelligence engine built for enterprise Java repositories. It combines **Abstract Syntax Tree (AST) lexical analysis**, **directed multigraph algorithms**, and **Git commit forensics** to compute mathematical ground truths about software architecture before invoking any probabilistic AI reasoning.

---

## 1. High-Level Architecture Pipeline

The system processes code through a five-stage deterministic pipeline:

```text
+-------------------------------------------------------------------------------+
| 1. INGESTION & PARSING (codecontext-core)                                     |
|    • JavaParser 3.25.8 with Bounded Concurrency                               |
|    • Pass 1: Fast declaration inventory (Classes, Records, Interfaces)        |
|    • Tri-Index ConcurrentSymbolTable with Flyweight String Interning          |
|    • Block-level LexicalScopeStack for local variable type inference          |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| 2. TWO-PASS INVOCATION RESOLUTION                                             |
|    • Pass 1.5: Type Hierarchy DAG tracking `extends` and `implements`          |
|    • Pass 2: 5-Step Chain-of-Responsibility Resolver                          |
|      1. Same-File Lookup  --> 2. Same-Package Resolution                      |
|      3. Explicit Imports  --> 4. Wildcard Imports                             |
|      5. External / Boundary Fallback                                          |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| 3. GRAPH INTELLIGENCE & TOPOLOGY (JGraphT)                                   |
|    • Directed Weighted Graph G = (V, E) with aggregated invocation details    |
|    • Boundary Node Weighting (JDK, Spring, Jackson filtered)                 |
|    • PageRank Centrality: Architectural gravity hubs & afferent coupling     |
|    • Kosaraju SCC Algorithm & Bounded DFS: Circular dependency loop detection |
|    • Transposed Graph BFS: Upstream change blast radius & caller trees        |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| 4. GIT EVOLUTION FORENSICS & RISK SCORING                                    |
|    • Pure Java Git integration via Eclipse JGit                               |
|    • 90-day churn analysis: additions, deletions, commit velocity             |
|    • Author entropy: multi-developer modification hotspots                    |
|    • CompositeRiskScore: dynamic weighting (PageRank + Cycles + Churn)       |
+-------------------------------------------------------------------------------+
                                       |
                    +------------------+------------------+
                    |                                     |
                    v                                     v
+---------------------------------------+ +-------------------------------------+
| 5. STANDALONE HTML VISUALIZER         | | 6. DEVELOPER CLI ENGINE             |
|    • Zero-dependency single-file HTML | |    • Picocli sub-100ms startup      |
|    • Interactive canvas               | |    • `analyze`: Full repo telemetry |
|    • 4 Lenses: Architecture Map,      | |    • `cycles`: CI gate & Tarjan SCC |
|      Component Inspector, Blast       | |    • `impact`: Blast radius tree    |
|      Radius, and Method Calls         | |    • `sarif`: OASIS SARIF 2.1.0     |
+---------------------------------------+ +-------------------------------------+
```

---

## 2. Core Modules & Responsibilities

### `codecontext-core`
- **Zero Framework Core:** Built with pure Java 21; zero Spring, web, or heavyweight framework dependencies.
- **AST Parsing & Resolution:** Uses JavaParser with bounded thread pools to parse source files into immutable `TypeDefinition` and `InvocationReference` models.
- **Security & Cryptographic Intelligence Foundation:**
  - `Finding`: Immutable domain representation of static analysis and architectural findings with exact source locations (`SourceRange`), severity, confidence, evidence, remediation, optional CWE identifiers, and stable finding identity deduplication (`Finding.deduplicate(...)`).
  - `CryptoApiDetector`: Accurately discovers and validates JCA/JCE/JSSE API calls (`Cipher`, `MessageDigest`, `Signature`, `Mac`, `KeyAgreement`, `KeyGenerator`, `KeyPairGenerator`, `SecureRandom`, `SSLContext`) using FQCNs, explicit imports, wildcard packages, and local class collision elimination (preventing false matches on custom classes like `FakeCipher`).
  - `LimitedExpressionResolver`: Deterministic static argument evaluator supporting direct string literals, string concatenations, final local variables, simple linear assignments, and static final constants with strict lexical scoping, assignment order enforcement, conditional branch invalidation, circular reference prevention, and bounded recursion.
  - `CryptoAlgorithm`: Representation and parser for JCA transformation strings (`algorithm/mode/padding`), providing detection helpers for ECB mode, legacy ciphers (DES, RC4), 3DES deprecation, weak digests (MD5, SHA-1), and obsolete TLS protocols.
  - `CryptoApiUsage`: Independent immutable inventory model tracking observed cryptographic operations, argument resolutions, and ownership confidence without prematurely classifying code as vulnerable.
  - `SecurityRule` & `AstVisitorSecurityRule`: Extensible rule contracts supporting AST expression traversal (`MethodCallExpr`, `ObjectCreationExpr`).
  - Built-in Security Rules:
    - `CC-CRYPTO-001`: Insecure ECB Mode Encryption (`Cipher.getInstance("AES/ECB/PKCS5Padding")` -> HIGH severity, CWE-327).
    - `CC-CRYPTO-002`: Legacy Insecure Cipher (DES, RC2, RC4 -> HIGH severity; 3DES/DESede -> MEDIUM severity per NIST SP 800-131A Rev 2 deprecation).
    - `CC-CRYPTO-003`: Weak Message Digest Usage (MD2, MD5, SHA-1 -> MEDIUM severity context-sensitive review, CWE-328).
    - `CC-CRYPTO-004`: Bare/Default Transformation Warning (e.g. `Cipher.getInstance("AES")` -> MEDIUM severity provider-dependent configuration warning).
    - `CC-TLS-001`: Obsolete TLS Protocol Selection (SSLv2, SSLv3, TLSv1, TLSv1.1 -> HIGH severity, CWE-326).
  - `SecurityRuleRegistry`: Thread-safe registry providing standard default cryptographic rules and clean configuration.
  - `SecurityAnalysisEngine`: High-performance, virtual-thread powered engine executing security analysis with bounded concurrency, graceful syntax error recovery, finding deduplication, and zero global AST retention.
- **Canonical Cryptographic Asset Inventory (Milestone 3):**
  - `CryptoPrimitiveType`: Standardized taxonomy covering `BLOCK_CIPHER`, `STREAM_CIPHER`, `MESSAGE_DIGEST` (mapped to `hash`), `MAC`, `SIGNATURE`, `KEY_AGREEMENT`, `KEY_GENERATOR`, `KEY_PAIR_GENERATOR`, `SECURE_RANDOM` (mapped to `drbg`), `TLS_PROTOCOL`, and `OTHER`.
  - `CryptoAssetUsage`: Immutable record tracking individual concrete usage coordinates, enclosing class/method, API owner, argument resolution, and confidence.
  - `CryptoAsset`: Normalized asset record (`assetId`, `name`, `algorithmFamily`, `primitiveType`, `mode`, `padding`, `usages`, `isResolved`, `confidence`) with deterministic sorting.
  - `CryptoAssetRelationship`: Structural relationship record (`sourceId`, `targetId`, `relationshipType`).
  - `CryptoInventorySummary`: High-level aggregated statistics covering total observations, unique assets, resolved assets, unresolved usages, and family/primitive counts.
  - `CryptoInventory`: Immutable canonical inventory model.
  - `CryptoInventoryEngine`: Aggregation engine grouping identical configurations into single assets, separating distinct cipher modes and paddings (e.g., `AES/GCM/NoPadding` vs `AES/CBC/PKCS5Padding`), maintaining stable machine-independent IDs, and retaining all usage coordinates without inventing key sizes or runtime properties.
- **Graph Modeling (JGraphT):** Represents the entire codebase as a directed weighted graph where vertices are classes/interfaces and edges denote method invocations and type couplings with logarithmic damping weights.
- **Analytics Engines:**
  - `PageRankEngine`: Computes architectural authority scores to identify central hubs.
  - `CycleDetectionEngine`: Employs Kosaraju's Strongly Connected Components algorithm with bounded DFS cycle path extraction in linear time $O(V + E)$.
  - `BlastRadiusEngine`: Inverts the dependency graph (`EdgeReversedGraph`) and performs breadth-first search to find all direct and transitive callers affected by a change.
  - `CompositeRiskEngine`: Merges coupling metrics with JGit commit churn and author counts to calculate risk scores (Low, Medium, High, Critical).
  - `HtmlReportExporter`: Generates the self-contained, offline interactive architectural blueprint (`codecontext-graph.html`).

### `codecontext-cli`
- **Developer Command Line Interface:** Built on Picocli 4.7.6 with sub-100ms cold startup time.
- **Subcommands:**
  - `analyze`: Scans code, displays top PageRank hotspots, and writes the HTML architectural report.
  - `cycles`: Detects architectural loops and provides the `--fail-on-cycles` CI quality gate.
  - `impact`: Bounded upstream blast radius tree and test scope suggestions.
  - `sarif`: Exports findings into OASIS SARIF 2.1.0 format with POSIX-normalized relative URIs.
  - `security scan`: Executes cryptographic and security rules against repository source trees with terminal table, SARIF 2.1.0, JSON, or interactive HTML output (`--format html`).
  - `crypto inventory`: Discovers and displays a normalized inventory of all cryptographic algorithms and APIs used in tabular text or JSON format.
  - `crypto cbom`: Generates an official CycloneDX 1.6 Cryptography Bill of Materials (CBOM) JSON document.
- **Export Adapters:**
  - `CycloneDxCbomExporter`: CycloneDX 1.6 compliant CBOM generator mapping canonical cryptographic assets into CycloneDX 1.6 JSON with native `cryptoProperties`, `algorithmProperties`, `primitive`, `mode`, `padding`, `cryptoFunctions`, occurrences in `evidence`, and dependency relationships. Verified against official JSON schema.
  - `SecurityHtmlReportExporter` & `SecurityReportPayload`: Dedicated, standalone, zero-dependency interactive HTML security dashboard (`codecontext-security.html`). Completely decoupled from architectural graph visualization (`HtmlReportExporter`). Provides 6 core sections: Overview Cards, Filterable Findings, Cryptographic Inventory, Visualizations & Charts, Unresolved Observations Table, and Static Analysis Limitations. Protected against HTML injection and XSS via DOM construction and JSON script-closing prevention.
  - `SarifExporter`: Generalized OASIS SARIF 2.1.0 exporter supporting both architectural findings (`CC001`, `CC002`) and security findings with exact line/column regions and POSIX relative URIs.
  - `ArchitecturalFindingAdapter`: Decouples graph analytics results from presentation by mapping cycles and hotspots into the canonical `Finding` model.
- **Terminal UX:** `TerminalCapabilityDetector` handles clean standard ASCII fallback on legacy terminals and respects the `NO_COLOR` standard.

---

## 3. Design Principles

1. **Deterministic Ground Truths:**
   CodeContext does not guess or hallucinate. It parses exact source code, traces real call sites, and uses formal graph algorithms to compute ground-truth dependencies.
2. **100% Offline & Air-Gapped:**
   All analysis runs locally in memory. Zero code, AST tokens, or telemetry leave the host machine, making it completely safe for enterprise and regulated industries.
3. **Zero-Dependency Visual Artifacts:**
   The generated HTML report is self-contained. It requires no local web server, no npm installations, and no internet connectivity to render interactive SVG/Canvas diagrams.
4. **Shift-Left CI/CD Alignment:**
   Adheres to POSIX exit codes (`0` for success, `1` for violation, `2` for invalid arguments) and standard SARIF 2.1.0 outputs to plug effortlessly into automated workflows.

---

## 4. Community & Support

* **Maintainer:** Abhishek Dwivedi
* **Email:** [abhishekdwivedi94443@gmail.com](mailto:abhishekdwivedi94443@gmail.com)
* **GitHub Repository:** [https://github.com/abhishek94443/CodeContext-Java](https://github.com/abhishek94443/CodeContext-Java)
* **Discussions:** [Join the conversation](https://github.com/abhishek94443/CodeContext-Java/discussions)
* **Issues:** [Report an issue](https://github.com/abhishek94443/CodeContext-Java/issues)
