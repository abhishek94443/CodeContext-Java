# CodeContext-Java

> **Deterministic Codebase Intelligence, Graph Analytics & Agentic AI Grounding for Enterprise Java**

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-9.2-blue.svg)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

CodeContext-Java is a deterministic codebase intelligence engine built with modern **Java 21**, **Virtual Threads**, and **JGraphT**. It parses Java repositories at line-level AST precision, builds inter-procedural directed dependency graphs, detects architectural circular loops (Tarjan SCC), calculates PageRank centrality and blast radius, and provides grounded context tools for AI coding agents via the **Model Context Protocol (MCP)**.

---

## Key Features

- **Mathematical Ground Truth First:** Hard facts are calculated using graph theory, AST symbol tables, and Git forensics before invoking any LLM.
- **Tarjan SCC Cycle Detection:** Reconstructs the exact sequence of circular dependency loops (`A -> B -> C -> A`) with bounded traversal safety.
- **PageRank Hotspot Analysis:** Identifies architectural gravity hubs and structural risk across thousands of classes in sub-second time.
- **Git Churn & Evolution Forensics:** Integrates with Eclipse JGit to evaluate 90-day churn, commit velocity, and author dispersion to score composite risk.
- **Interactive Visual Blueprint:** Generates standalone, zero-dependency interactive HTML graph reports (`HtmlReportExporter`).
- **Shift-Left CLI & CI Gating:** Fast sub-100ms Picocli CLI featuring `--fail-on-cycles` CI gating and SARIF 2.1.0 PR annotations.
- **Model Context Protocol (MCP) Ready:** Designed for seamless integration with Cursor, Claude Desktop, and autonomous agents.

---

## Architecture Overview

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ CodeContext Architecture Pipeline                                           │
│                                                                             │
│  [Source .java Files]                                                       │
│          │                                                                  │
│          ▼                                                                  │
│  ┌─────────────────────────┐                                                │
│  │ Pass 1 Scanner          │ ──► Bounded Virtual Threads & SymbolTable      │
│  └───────────┬─────────────┘                                                │
│              ▼                                                              │
│  ┌─────────────────────────┐                                                │
│  │ Pass 2 Resolver         │ ──► 6-Step Chain-of-Responsibility Resolution  │
│  └───────────┬─────────────┘                                                │
│              ▼                                                              │
│  ┌─────────────────────────┐                                                │
│  │ JGraphT Directed Graph  │ ──► PageRank, Tarjan SCC Cycles, Blast Radius  │
│  └───────────┬─────────────┘                                                │
│              ├──────────────────────┬──────────────────────┐                │
│              ▼                      ▼                      ▼                │
│     [Interactive HTML UI]   [Picocli CLI Engine]    [Spring AI / MCP]       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Quickstart

### Prerequisites
- **JDK 21** or higher (`java -version`)

### Build the Project
```bash
./gradlew build
```

### Run Repository Analysis
```bash
./gradlew :codecontext-cli:installDist
./codecontext-cli/build/install/codecontext/bin/codecontext analyze <path-to-java-repo>
```

### Detect Circular Dependencies
```bash
./codecontext-cli/build/install/codecontext/bin/codecontext cycles <path-to-java-repo> --fail-on-cycles
```

---

## Documentation

- [Phase 1: Core AST Parser Engine](docs/PHASE_1_CORE_PARSER.md)
- [Phase 2: Graph Intelligence Engine](docs/PHASE_2_GRAPH_INTELLIGENCE.md)
- [Phase 3: Developer CLI & Enterprise Governance](docs/PHASE_3_CLI_EXPERIENCE.md)
- [Phase 4: Spring AI & Model Context Protocol (MCP)](docs/PHASE_4_AI_RAG_MCP.md)
- [Interactive UI Visualizer Specification](docs/ui-spec.md)

---

## License

Distributed under the Apache 2.0 License.
