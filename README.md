# CodeContext-Java

> **The High-Performance CLI & Architectural Intelligence Engine for Java Codebases**  
> *Understand your architecture. Predict change blast radius. Prevent regressions before you commit.*

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Build Status](https://img.shields.io/badge/Tests-135%20Passing%20(100%25)-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/Architecture-100%25%20Air--Gapped%20%26%20Offline-blueviolet.svg)]()
[![Discussions](https://img.shields.io/badge/Community-Discussions-blue?logo=github)](https://github.com/abhishek94443/CodeContext-Java/discussions)
[![Issues](https://img.shields.io/badge/Support-Issues-red?logo=github)](https://github.com/abhishek94443/CodeContext-Java/issues)

---

## What is CodeContext-Java?

Think of CodeContext as an **interactive GPS and automated architectural safety gate for your software**.

In any growing Java codebase, making changes can be stressful. A developer edits what looks like a simple method, but hours later, builds or production break because that method was silently called by dozens of other classes across multiple packages.

**CodeContext-Java eliminates that guesswork through a lightning-fast CLI:**
1. **Maps your entire system:** Analyzes Java source code to show how every class, method, and package connects.
2. **Finds hidden bottlenecks:** Uses PageRank mathematics to pinpoint critical architectural hubs.
3. **Calculates Blast Radius in seconds:** Runs `codecontext impact <Class>` to show exactly who calls your class and what test suites to run *before* you touch code.
4. **Catches Architectural Cycles:** Uses Tarjan's SCC algorithm to detect circular dependencies (`A -> B -> C -> A`), highlighting the weakest link so you can decouple them cleanly.
5. **Generates an Offline Visual Blueprint:** Exports an interactive, single-file HTML architecture report (`codecontext-graph.html`) requiring zero servers and zero internet.

---

## 1-Minute Quick Start

### Prerequisites
* **Java 21+ SDK** installed on your system (`java -version`).
* Works on Windows, macOS, and Linux.

### 1. Clone & Build from Source
```bash
# Clone the repository
git clone https://github.com/abhishek94443/CodeContext-Java.git
cd CodeContext-Java

# Build and assemble the standalone CLI distribution
./gradlew :codecontext-cli:installDist
```
*(On Windows, run `gradlew.bat :codecontext-cli:installDist`).*

### 2. Run the CLI
The compiled standalone binary is ready in `codecontext-cli/build/install/codecontext/bin/`:

```bash
# On Windows:
codecontext-cli\build\install\codecontext\bin\codecontext.bat [COMMAND] [OPTIONS]

# On Linux or macOS:
./codecontext-cli/build/install/codecontext/bin/codecontext [COMMAND] [OPTIONS]
```

*(Tip: Add the `bin/` folder to your system `PATH` to run simply as `codecontext [COMMAND]` from any folder).*

---

## The 4 Core CLI Commands Working Today

CodeContext-Java features a fast, focused CLI designed for everyday developer workflows:

| Command | What It Does (Plain English) | Typical Use Case |
| :--- | :--- | :--- |
| **`codecontext analyze [PATH]`** | Scans your Java codebase, ranks the Top 10 architectural hotspots, and generates the interactive visual report. | When exploring a new codebase, performing architectural reviews, or onboarding developers. |
| **`codecontext cycles [PATH]`** | Detects all circular dependency loops in linear time $O(V + E)$. | Finding architectural loops locally or enforcing clean architecture in CI pipelines. |
| **`codecontext impact <Class>`** | Shows an indented upstream blast radius tree of all callers and automatically suggests test suites to run. | In your terminal right before you refactor or modify an existing class. |
| **`codecontext sarif [PATH]`** | Emits findings in standard OASIS SARIF 2.1.0 format with POSIX relative paths. | Generating machine-readable static analysis reports for code review platforms. |

> 📖 **Looking for all flags, options, and behaviors?**  
> Check the complete [**Command Book (`COMMAND_BOOK.md`)**](COMMAND_BOOK.md) for full syntax and examples.

---

## Why Developers & Teams Choose CodeContext-Java

### 1. 100% Air-Gapped & Private (Zero Data Leaks)
Unlike cloud-dependent code analysis tools, CodeContext runs **100% locally in your machine's memory**. Not a single line of code, AST token, or telemetry data ever leaves your computer. It is completely safe for enterprise, banking, healthcare, and air-gapped security environments.

### 2. Sub-100ms Startup & High Performance
Built with pure Java 21, Picocli, and JGraphT, CodeContext launches in **under 80ms** and parses hundreds of classes in seconds without heavy background daemons or bloated memory footprints.

### 3. Plain-English Architecture Insights
You don't need a background in graph theory to understand CodeContext metrics:
* **Architectural Hotspot:** A class that sits at the center of your codebase (like a major transit hub). If a hotspot breaks, the ripple effect is large.
* **In-Degree (Callers):** How many other classes depend on this component.
* **Blast Radius:** The full tree of direct and transitive callers impacted if you modify a class.
* **Architectural Cycle:** When classes mutually depend on each other (`A -> B -> A`), hindering modularity. CodeContext identifies the weakest link to break.

---

## CI/CD & Automation Overview

CodeContext-Java is built from the ground up to integrate cleanly into automated pipelines:

1. **Deterministic POSIX Exit Codes:**
   - Exit Code `0`: Clean execution, zero violations.
   - Exit Code `1`: Quality gate tripped (e.g. `codecontext cycles . --fail-on-cycles` immediately stops the build if circular dependencies exist).
   - Exit Code `2`: Invalid arguments or non-existent class names.
2. **Standard OASIS SARIF 2.1.0 Output:**
   - The `codecontext sarif` command emits standard SARIF with normalized forward-slash paths (`src/main/java/...`), ready for ingestion by GitHub Code Scanning, SonarQube, or custom CI tooling to display inline pull request annotations.
3. **Downloadable Architecture Artifacts:**
   - CI pipelines can run `codecontext analyze` and save `codecontext-graph.html` as a downloadable build artifact, giving reviewers an offline interactive architecture map for every build.

---

## Community, Discussions & Contributing

CodeContext-Java is open-source and actively maintained. Community discussions and issue tracking are open:

* 💬 **Have questions, ideas, or feedback?** Join our [**GitHub Discussions**](https://github.com/abhishek94443/CodeContext-Java/discussions).
* 🐛 **Found a bug or need a feature?** Open an issue on [**GitHub Issues**](https://github.com/abhishek94443/CodeContext-Java/issues).
* 📧 **Contact & Maintainer:** Abhishek Dwivedi ([abhishekdwivedi94443@gmail.com](mailto:abhishekdwivedi94443@gmail.com)).

---

## Architecture & System Design

Curious about how CodeContext-Java parses Abstract Syntax Trees, constructs directed multigraphs, and calculates PageRank authority?  
Read the high-level system design in [**`ARCHITECTURE.md`**](ARCHITECTURE.md).

---

## License

Distributed under the Apache 2.0 License. Built with Java 21, JavaParser, JGraphT, and Picocli.