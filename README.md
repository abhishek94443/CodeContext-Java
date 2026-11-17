# CodeContext-Java

> **Architecture and Cryptographic Security Analysis for Java Codebases**
>
> *Explore source dependencies. Find risky cryptographic usage. Assess change impact and post-quantum migration needs.*

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Build Status](https://img.shields.io/badge/Tests-See%20verification%20report-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/Architecture-100%25%20Air--Gapped%20%26%20Offline-blueviolet.svg)]()
[![Discussions](https://img.shields.io/badge/Community-Discussions-blue?logo=github)](https://github.com/abhishek94443/CodeContext-Java/discussions)
[![Issues](https://img.shields.io/badge/Support-Issues-red?logo=github)](https://github.com/abhishek94443/CodeContext-Java/issues)

---

## What is CodeContext-Java?

**CodeContext-Java is a local command-line tool that helps you understand a Java project's architecture and its use of cryptography.** It reads Java source code and produces terminal output, machine-readable results and two separate interactive HTML reports. You do not need to run the application being analyzed.

It helps answer two practical questions: **“Which classes could be affected by this change?”** and **“Where does this project use cryptography that needs review?”**

### Understand the architecture

CodeContext uses JavaParser to collect declarations and analyze supported invocation patterns, then builds a class dependency graph with JGraphT. It ranks architectural hubs with PageRank, detects strongly connected components with Kosaraju's algorithm, extracts bounded cycle examples and follows upstream dependencies to estimate a class's blast radius. The architecture dashboard lets you explore these relationships visually.

### Understand cryptographic security

CodeContext detects supported Java cryptographic API calls and resolves algorithm names from literals and supported local expressions. Its cryptography workflow includes:

- **Security scanning:** Five rules flag ECB mode, legacy ciphers, weak message digests, provider-dependent default cipher transformations and obsolete SSL/TLS protocol selections. Findings include source locations, evidence and remediation guidance.
- **Cryptographic inventory:** Groups observed algorithms and configurations into assets, showing where each is used. For example, AES encryption and AES key generation are distinct inventory assets.
- **Cryptographic Bill of Materials (CBOM):** Exports the inventory as CycloneDX 1.6 JSON for further analysis and tooling. Schema validation checks structure; it does not prove the inventory captures every runtime cryptographic behavior.
- **Post-quantum cryptography (PQC) assessment:** Classifies observed assets by quantum exposure and classical weakness, suggests migration directions, and uses class dependencies to estimate upstream impact. A TLS API call alone does not prove a quantum-vulnerable key exchange; insufficient evidence remains unknown.
- **Security dashboard:** Presents findings, inventory and PQC assessments in an independent offline HTML report.

Both workflows use **static source analysis**. Their results support investigation: they do not guarantee runtime execution paths, complete symbol resolution or detection of every vulnerability. Dynamic values and unsupported constructs can remain unresolved.

## Explore the example HTML reports

These are separate, self-contained reports generated from the correctness-remediated implementation:

| Report | Example project | What you can explore |
| :--- | :--- | :--- |
| **[Architecture report — Gson](examples/gson-architecture-report.html)** | Gson | Class dependencies, architectural hotspots, cycle examples and upstream impact. |
| **[Cryptography and security report — BenchmarkJava](examples/benchmarkjava-security-report.html)** | OWASP BenchmarkJava | Cryptographic findings, asset inventory, PQC assessment and affected classes. BenchmarkJava deliberately contains vulnerable examples. |

**To open either report from GitHub:** follow its link, download the HTML file using the file page's download button, then open the downloaded file in your browser. The repository file view is not the interactive dashboard. You can also clone the repository and open the files directly from the `examples/` folder.

No server or internet connection is needed to use the downloaded reports. Search, tabs, graph selection and filters were manually confirmed working offline. These are saved example snapshots; use the CLI to generate reports for your own project.

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

## Core CLI Commands

CodeContext-Java features a fast, focused CLI designed for everyday developer workflows and security analysis:

### Architectural Intelligence
| Command | What It Does (Plain English) | Typical Use Case |
| :--- | :--- | :--- |
| **`codecontext analyze [PATH]`** | Scans your Java codebase, ranks the Top 10 architectural hotspots, and generates the interactive visual report. | When exploring a new codebase, performing architectural reviews, or onboarding developers. |
| **`codecontext cycles [PATH]`** | Finds SCCs in $O(V + E)$ and extracts bounded cycle examples; enumeration may be truncated. | Finding architectural loops locally or enforcing clean architecture in CI pipelines. |
| **`codecontext impact <Class>`** | Shows an indented upstream blast radius tree of all callers and automatically suggests test suites to run. | In your terminal right before you refactor or modify an existing class. |
| **`codecontext sarif [PATH]`** | Emits findings in standard OASIS SARIF 2.1.0 format with POSIX relative paths. | Generating machine-readable static analysis reports for code review platforms. |

### Cryptographic Intelligence & Security Analysis
| Command | What It Does (Plain English) | Typical Use Case |
| :--- | :--- | :--- |
| **`codecontext security scan [PATH]`** | Scans for cryptographic vulnerabilities (ECB mode, weak digests, deprecated ciphers, obsolete TLS) with terminal, SARIF, JSON, or interactive HTML dashboard output (`--format html`). | Automated security audits, pull request checks, and security dashboard generation. |
| **`codecontext crypto inventory [PATH]`** | Discovers and normalizes supported cryptographic API usages, algorithms, primitives, and modes observed in source into tabular text or JSON. | Cryptographic asset discovery, auditing cryptographic agility, and compliance reviews. |
| **`codecontext crypto cbom [PATH]`** | Exports a CycloneDX 1.6 CBOM (`crypto-bom.json`); regression fixtures are checked against the pinned schema. Schema validity does not establish semantic completeness. | Supply chain security, post-quantum readiness assessments, and cryptographic BOM compliance. |
| **`codecontext crypto pqc [PATH]`** | Assesses observed assets for quantum exposure, migration considerations and upstream class impact; preserves unknown evidence. | Planning a cryptographic migration and identifying classes to review. |

### Generate reports for your project

```bash
# Architecture dashboard: writes codecontext-graph.html in the output folder
codecontext analyze /path/to/project --output ./architecture-output

# Independent security dashboard
codecontext security scan /path/to/project -o ./security-report.html

# Machine-readable cryptographic inventory, CBOM and PQC assessment
codecontext crypto inventory /path/to/project --format json -o ./crypto-inventory.json
codecontext crypto cbom /path/to/project -o ./crypto-bom.json
codecontext crypto pqc /path/to/project --format json -o ./pqc-assessment.json
```

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

Curious about how CodeContext-Java parses Abstract Syntax Trees, constructs directed dependency graphs, and calculates PageRank authority?
Read the high-level system design in [**`ARCHITECTURE.md`**](ARCHITECTURE.md).

---

## License

Distributed under the Apache 2.0 License. Built with Java 21, JavaParser, JGraphT, and Picocli.
See [correctness remediation and limitations](CORRECTNESS_REMEDIATION.md) for verified results, metric definitions, and remaining analysis boundaries.
