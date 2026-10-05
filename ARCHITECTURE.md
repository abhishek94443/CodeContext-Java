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
|    • Pass 2: 6-Step Chain-of-Responsibility Resolver                          |
|      1. Same-File Lookup  --> 2. Same-Package Resolution                      |
|      3. Explicit Imports  --> 4. Wildcard Imports (zero false edges)          |
|      5. Inherited Methods --> 6. External / Boundary Fallback                 |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| 3. GRAPH INTELLIGENCE & TOPOLOGY (JGraphT)                                   |
|    • Directed Weighted Multigraph G = (V, E)                                  |
|    • Boundary Node Weighting (JDK, Spring, Jackson filtered)                 |
|    • PageRank Centrality: Architectural gravity hubs & afferent coupling     |
|    • Tarjan SCC Algorithm: Exact architectural circular dependency loops      |
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
- **Graph Modeling (JGraphT):** Represents the entire codebase as a directed weighted multigraph where vertices are classes/interfaces and edges denote method invocations and type couplings.
- **Analytics Engines:**
  - `PageRankEngine`: Computes architectural authority scores to identify central hubs.
  - `CycleDetectionEngine`: Employs Tarjan's Strongly Connected Components algorithm to find cyclic dependencies in linear time $O(V + E)$.
  - `BlastRadiusEngine`: Inverts the dependency graph (`EdgeReversedGraph`) and performs breadth-first search to find all direct and transitive callers affected by a change.
  - `CompositeRiskEngine`: Merges coupling metrics with JGit commit churn and author counts to calculate risk scores (Low, Medium, High, Critical).
  - `HtmlReportExporter`: Generates the self-contained, offline interactive visual blueprint (`codecontext-graph.html`).

### `codecontext-cli`
- **Developer Command Line Interface:** Built on Picocli 4.7.6 with sub-100ms cold startup time.
- **Subcommands:**
  - `analyze`: Scans code, displays top PageRank hotspots, and writes the HTML report.
  - `cycles`: Detects architectural loops and provides the `--fail-on-cycles` CI quality gate.
  - `impact`: Bounded upstream blast radius tree and test scope suggestions.
  - `sarif`: Exports findings into OASIS SARIF 2.1.0 format with POSIX-normalized relative URIs.
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
