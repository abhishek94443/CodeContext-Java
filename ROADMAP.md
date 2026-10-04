# Project Roadmap

This roadmap outlines the completed foundational milestones and future directions for **CodeContext-Java**.

---

## 🟢 Completed in Current Release (v0.1)

- [x] **Core AST Parsing Engine:**
  - Fast 2-pass Java 21 scanner using JavaParser and Virtual Threads.
  - Tri-index `ConcurrentSymbolTable` with memory-optimized string flyweight interning.
  - Block-level `LexicalScopeStack` tracking local variable bindings.
- [x] **Accurate Invocation Resolution:**
  - 6-step Chain-of-Responsibility resolving same-file, same-package, explicit imports, wildcards, and inheritance DAG.
  - Bounded stress-tested on repositories up to 5,000+ classes.
- [x] **Graph Analytics & Topology:**
  - In-memory directed multigraph constructed via JGraphT.
  - PageRank centrality scoring to surface structural hubs.
  - Tarjan Strongly Connected Components (SCC) circular dependency detection with ordered loop path extraction.
  - Transposed BFS blast radius computation.
- [x] **Git Evolution Forensics:**
  - Eclipse JGit integration for 90-day churn, commit velocity, and author entropy.
  - `CompositeRiskScore` synthesizing architectural authority, circular risk, and modification velocity.
- [x] **Developer CLI:**
  - Picocli root runner with `analyze` and `cycles` commands.
  - Automated CI gating (`--fail-on-cycles`) with standard sysexits exit codes.
  - Multi-platform startup scripts with Windows line-length handling.
- [x] **Standalone HTML Visualizer:**
  - Zero-dependency single-file HTML report exporter (`examples/gson-architecture-report.html`).

---

## 🟡 In Progress / Upcoming Milestones (v0.2 - v1.0)

### 1. Model Context Protocol (MCP) Server
- Expose deterministic codebase intelligence to AI coding assistants (Cursor, Claude Desktop, Antigravity) via Anthropic's **Model Context Protocol (MCP)**.
- Standard tools:
  - `getBlastRadius(classOrFilePath)`: Pre-edit caller impact verification.
  - `getHotspots(limit)`: Central architectural classes ranked by PageRank.
  - `detectCycles()`: Trace circular dependency loops in active development.

### 2. Spring AI & Graph-Augmented RAG (G-RAG)
- Semantic vector indexing aligned with AST method/class boundaries (no arbitrary character splitting).
- Hybrid blending: grounding semantic code retrieval with deterministic graph facts (PageRank rank, callers, and cycles).

### 3. GitHub Action & PR Review Gate
- Turnkey GitHub Action that comments on Pull Requests with visual blast radius maps and automated cycle warnings.
- SARIF 2.1.0 report generation for native GitHub Code Scanning annotations.

### 4. Cross-Repository & Microservice Graphs
- Multi-repository dependency mapping linking independent microservices through contract interfaces and API boundaries.
