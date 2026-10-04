# CodeContext-Java

> **Deterministic Codebase Intelligence, Graph Analytics & Shift-Left Architecture for Enterprise Java**

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-9.2-blue.svg)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Live Demo](https://img.shields.io/badge/Live_Demo-Google_Gson_Report-2ea44f?style=for-the-badge&logo=googlechrome&logoColor=white)](https://raw.githack.com/abhishek94443/CodeContext-Java/main/examples/gson-architecture-report.html)

CodeContext-Java is a deterministic codebase intelligence engine built with modern **Java 21**, **Virtual Threads**, and **JGraphT**. It parses Java repositories at line-level AST precision, builds inter-procedural directed dependency graphs, detects architectural circular loops (Tarjan SCC), calculates PageRank centrality and change blast radius, and generates interactive zero-dependency visual blueprints.

---

## 🚀 View Live Interactive Demo

Experience the full power of CodeContext-Java directly in your browser without installing anything or running a build:

👉 **[Launch Live Google Gson Architecture Blueprint](https://raw.githack.com/abhishek94443/CodeContext-Java/main/examples/gson-architecture-report.html)**  
*(Alternative mirror: [View on HTMLPreview](https://htmlpreview.github.io/?https://github.com/abhishek94443/CodeContext-Java/blob/main/examples/gson-architecture-report.html) | [GitHub Pages](https://abhishek94443.github.io/CodeContext-Java/))*

### What You Are Exploring in This Demo
To demonstrate how CodeContext-Java analyzes real-world enterprise codebases, we ran our engine against the official public [google/gson](https://github.com/google/gson) repository (one of the world's most widely used Java libraries). 

When you open the interactive canvas, you can directly inspect:
* **722 Production Classes & Interfaces:** Completely parsed, symbol-resolved, and mapped into a directed multigraph.
* **Topological PageRank Centrality:** Visually identifying the core architectural gravity hubs (`com.google.gson.Gson`, `TypeAdapter`, `JsonElement`).
* **Circular Dependency Tracing:** The engine detected **12 circular reference loops** in Gson (such as `Gson -> TypeAdapter -> GsonBuilder -> Gson`). You can trace every step of these cycles directly on the graph.
* **Compound Package Clustering:** Classes are grouped into interactive bounding boxes by their package namespaces (`internal.bind`, `stream`, `reflect`), allowing you to zoom, pan, search, and focus on specific sub-systems.
* **100% Zero-Dependency Standalone HTML:** The entire visualizer is self-contained in a single file ([`examples/gson-architecture-report.html`](examples/gson-architecture-report.html)) powered by pure client-side SVG and Canvas rendering.

---

## Key Features

- **Mathematical Ground Truth First:** Hard facts are calculated using graph theory, AST symbol tables, and Git forensics before invoking any LLM.
- **Tarjan SCC Cycle Detection:** Reconstructs the exact sequence of circular dependency loops (`A -> B -> C -> A`) with bounded traversal safety.
- **PageRank Hotspot Analysis:** Identifies architectural gravity hubs and structural risk across thousands of classes in sub-second time.
- **Git Churn & Evolution Forensics:** Integrates with Eclipse JGit to evaluate 90-day churn, commit velocity, and author dispersion to score composite risk.
- **Interactive Visual Blueprint:** Generates standalone, zero-dependency interactive HTML graph reports (`HtmlReportExporter`).
- **Shift-Left CLI & CI Gating:** Fast sub-100ms Picocli CLI featuring `--fail-on-cycles` CI gating and standard sysexits exit codes.

---

## Architecture Pipeline

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
│              ├──────────────────────────────────────┐                       │
│              ▼                                      ▼                       │
│     [Interactive HTML Visualizer]           [Picocli CLI Engine]            │
└─────────────────────────────────────────────────────────────────────────────┘
```

For detailed pipeline design, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Quickstart

### Prerequisites
- **JDK 21** or higher (`java -version`)

### Build
```bash
./gradlew build
```

### Install CLI
```bash
./gradlew :codecontext-cli:installDist
```

### Analyze a Codebase & Generate Interactive Report
```bash
./codecontext-cli/build/install/codecontext/bin/codecontext analyze <path-to-java-repo>
```

### Check Circular Dependencies in CI
```bash
./codecontext-cli/build/install/codecontext/bin/codecontext cycles <path-to-java-repo> --fail-on-cycles
```

---

## Project Structure

```text
codecontext-java/
├── codecontext-core/      # Pure Java 21 engine (AST parsing, JGraphT, JGit forensics)
├── codecontext-cli/       # Picocli command-line runner (analyze, cycles, HTML export)
├── codecontext-service/   # Spring Boot foundation for future agent tool services
├── examples/              # Standalone demonstration reports (e.g., Google Gson)
├── ARCHITECTURE.md        # Technical architecture and pipeline breakdown
├── ROADMAP.md             # Completed features and upcoming AI/MCP milestones
├── LICENSE                # Apache 2.0 License
└── README.md              # Project overview and quickstart
```

---

## Roadmap

See [ROADMAP.md](ROADMAP.md) for current progress and upcoming features, including **Model Context Protocol (MCP)** tool integration for AI agents (Cursor / Claude Desktop), **AST-boundary semantic RAG**, and automated **GitHub Action PR gating**.

---

## License

Distributed under the Apache 2.0 License. See [LICENSE](LICENSE) for details.
