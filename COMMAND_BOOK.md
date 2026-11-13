# CodeContext CLI — Command Book

> **Target Audience:** Developers, Tech Leads, QA Engineers, and DevOps (Beginner to Advanced)  
> **Engine:** Java 21 · Picocli 4.7+ · JGraphT 1.5.2 · JGit 7.1.0  
> **Repository:** [https://github.com/abhishek94443/CodeContext-Java](https://github.com/abhishek94443/CodeContext-Java) · **Maintainer:** Abhishek Dwivedi ([abhishekdwivedi94443@gmail.com](mailto:abhishekdwivedi94443@gmail.com))  
> **Executable Location:** `codecontext-cli/build/install/codecontext/bin/codecontext` (Linux/macOS) or `codecontext.bat` (Windows)

Welcome to the **CodeContext Command Book**. This document is the practical, living reference guide for every command and option currently supported by the CodeContext command-line interface.

> **Living Documentation Principle:**  
> This book **only** documents commands and flags that are **100% implemented, tested, and actively working today**. As new commands are created and verified in the codebase, they will be added here.

---

## 1. Quick Start: How to Run CodeContext

Once built via `./gradlew :codecontext-cli:installDist`, you can run CodeContext directly from your terminal:

### On Windows:
```cmd
codecontext-cli\build\install\codecontext\bin\codecontext.bat [COMMAND] [OPTIONS]
```

### On Linux or macOS:
```bash
./codecontext-cli/build/install/codecontext/bin/codecontext [COMMAND] [OPTIONS]
```

### Adding to PATH (Run `codecontext` from anywhere):
* **Windows (PowerShell):**
  ```powershell
  $env:Path += ";$PWD\codecontext-cli\build\install\codecontext\bin"
  ```
* **macOS / Linux (Bash/Zsh):**
  ```bash
  export PATH="$PATH:$PWD/codecontext-cli/build/install/codecontext/bin"
  ```

To see all available commands and global options at any time, run:
```bash
codecontext --help
```

---

## 2. Global Exit Codes (How Scripts & CI Know What Happened)

Whenever CodeContext finishes running, it returns an **exit code** (a standard integer returned to the operating system shell). Automated CI/CD pipelines use this code to decide whether a build passes or fails:

| Exit Code | Meaning | When It Happens |
| :---: | :--- | :--- |
| **`0`** | **SUCCESS** | Everything completed normally. No architectural violations were found. |
| **`1`** | **VIOLATION** | An architectural rule was broken (for example, cycles were found while `--fail-on-cycles` was active). |
| **`2`** | **INVALID ARGUMENTS** | You typed an unrecognized flag, non-existent target class, or a path that does not exist. |
| **`3`** | **I/O ERROR** | The tool could not read or write a file on your disk. |
| **`4`** | **INTERNAL ERROR** | An unexpected error occurred while parsing code. |

---

## 3. Currently Supported Commands

Today, CodeContext supports four core commands:

1. **[`codecontext analyze`](#31-codecontext-analyze)** — Complete codebase architecture scan, PageRank hotspot calculation, and offline interactive HTML Visual Blueprint generation.
2. **[`codecontext cycles`](#32-codecontext-cycles)** — Fast Tarjan SCC cycle detector and CI/CD quality gate to catch circular dependency loops.
3. **[`codecontext impact`](#33-codecontext-impact)** — Depth-bounded blast radius tree analysis with automated regression test-suite recommendations.
4. **[`codecontext sarif`](#34-codecontext-sarif)** — Standard OASIS SARIF 2.1.0 exporter with POSIX relative paths for native GitHub Actions PR code-scanning annotations.

---

### 3.1. `codecontext analyze`

#### What Does It Do?
The `analyze` command is the main intelligence tool. When you run it on a repository:
1. It scans and parses your Java source files into an Abstract Syntax Tree (AST).
2. It resolves method calls and class relationships across files and packages.
3. It constructs the entire directed dependency graph.
4. It calculates **PageRank authority scores** to identify the most critical "hub" classes in your architecture.
5. It inspects Git history (if Git is available) to measure code churn and author counts.
6. It evaluates composite risk (coupling + importance + change frequency).
7. It generates a self-contained, offline interactive **Visual Blueprint HTML report** (`codecontext-graph.html`).

#### Command Syntax:
```bash
codecontext analyze [PATH] [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `[PATH]` | Argument | `.` *(Current Directory)* | The folder of the Java project you want to analyze. |
| `--output <DIR>` | Folder Path | `<REPO>/build/reports` | Specific folder where `codecontext-graph.html` will be saved. |
| `--json` | Flag | `false` | Prints raw JSON data to the terminal instead of human-friendly tables. |
| `-q`, `--quiet` | Flag | `false` | Suppresses banners and tables; prints only the path to the generated HTML report. |
| `--no-color` | Flag | `false` | Disables colored text in the terminal (useful for saving logs to a plain file). |
| `--include-tests` | Flag | `false` | Includes test directories (e.g. `src/test/java`) in the architecture scan. |

---

#### What Happens: With vs. Without Options

##### 1. Path Argument (`[PATH]`)
* **Without `[PATH]` (`codecontext analyze`):** Scans the folder you are currently standing in.
* **With `[PATH]` (`codecontext analyze C:\Projects\MyRepo`):** Scans the specified folder without needing to `cd` into it.

##### 2. `--output` Option
* **Without `--output`:** The report is saved to `build/reports/codecontext-graph.html` inside the scanned repository.
* **With `--output /my/custom/dir`:** The report is saved to `/my/custom/dir/codecontext-graph.html`.

##### 3. `--json` Option
* **Without `--json`:** Prints a readable ANSI terminal dashboard with high-contrast borders and the Top 10 Hotspots table.
* **With `--json`:** Prints structured JSON output to standard output, making it easy for Python, Node.js, or `jq` scripts to parse.

---

### 3.2. `codecontext cycles`

#### What Does It Do?
The `cycles` command detects **Architectural Cycles** (circular dependencies where Component A depends on B, which depends on C, which depends back on A).
* It uses Tarjan's Strongly Connected Components (SCC) algorithm to identify loops in linear time $O(V + E)$.
* It lists every cycle chain so engineers know exactly which classes are trapped in the mutual dependency.
* It can act as an **automated CI/CD quality gate** to stop new cycles from being merged into your repository.

#### Command Syntax:
```bash
codecontext cycles [PATH] [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `[PATH]` | Argument | `.` *(Current Directory)* | The folder of the Java project you want to check for cycles. |
| `--fail-on-cycles` | Flag | `false` | **CI/CD Quality Gate:** Fails with exit code `1` if any circular dependencies are detected. |
| `--json` | Flag | `false` | Prints the cycles as a clean JSON array instead of bullet points. |
| `--include-tests` | Flag | `false` | Includes test classes in cycle detection. |

---

#### What Happens: With vs. Without Options

##### 1. `--fail-on-cycles` Option (The CI Gate)
* **Without `--fail-on-cycles` (Developer Inspection Mode):**
  - Lists any cycles found to the terminal and **always returns exit code `0`**.
* **With `--fail-on-cycles` (Automated Quality Gate Mode):**
  - If **0 cycles exist**: Prints `Clean architecture: 0 circular dependencies detected.` and exits with **`0` (PASS)**.
  - If **1 or more cycles exist**: Prints error message `CI Gate Violation: N circular dependency loops detected!` and exits with **`1` (FAIL)**.

---

### 3.3. `codecontext impact`

#### What Does It Do?
The `impact` command calculates the **Blast Radius** of modifying a specific class or source file.
* It queries the reversed dependency graph to reveal all direct and transitive callers that rely on the target class.
* It bounds search depth to prevent terminal buffer flooding on major architectural hubs.
* It computes the cumulative risk score of changing the component based on caller PageRank centrality.
* It automatically searches the test suites to recommend which unit test classes you should run before committing.

#### Command Syntax:
```bash
codecontext impact <TARGET_CLASS> [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `<TARGET_CLASS>` | Parameter | *(Required)* | The simple name (e.g. `OrderService`) or FQCN (e.g. `com.example.service.OrderService`). |
| `--path <DIR>` | Path | `.` *(Current Directory)* | Repository root directory to analyze. |
| `-d`, `--depth <N>` | Integer | `3` | Maximum number of upstream hops to display in the transitive caller tree. |
| `--suggest-tests` | Flag | `false` | Automatically scans test folders and recommends test suites matching the impacted classes. |
| `--json` | Flag | `false` | Outputs the complete impact hierarchy and caller depths as structured JSON. |
| `--no-color` | Flag | `false` | Disables ANSI coloring. |

---

#### What Happens: With vs. Without Options

##### 1. `--depth <N>` Option
* **Without `--depth` (Default: 3):** Traces callers up to 3 levels deep (`Class -> Caller (depth 1) -> Caller (depth 2) -> Caller (depth 3)`).
* **With `--depth 1`:** Traces only direct immediate callers.
* **With `--depth 5`:** Deeply inspects upstream callers across 5 architecture layers.

##### 2. `--suggest-tests` Option
* **Without `--suggest-tests`:** Only displays architectural production callers.
* **With `--suggest-tests`:** Scans project test directories and outputs a dedicated `Recommended Test Scope:` list showing exact test suites covering the impacted classes.

##### 3. `--json` Option
* **Without `--json`:** Renders a clean terminal tree.
* **With `--json`:** Emits structured JSON with `inDegree`, `directCallers`, `transitiveCallers`, and `recommendedTestScope`.

#### Real Example Run:
```bash
codecontext impact OrderService --suggest-tests --depth 2
```
**Terminal Output:**
```text
================================================================================
 Blast Radius Analysis: OrderService (com.example.service.OrderService)
================================================================================
 Direct Callers (In-degree: 1):
   +-- com.example.controller.OrderController

 Transitive Upstream Tree (Depth: 2):
   [OrderService]
       <-- [OrderController] (depth 1)
             <-- [ApiGateway] (depth 2)
--------------------------------------------------------------------------------
 Total Blast Radius: 2 impacted classes
 Cumulative Risk Score: 0.0450
--------------------------------------------------------------------------------
 Recommended Test Scope:
   - OrderServiceTest
   - OrderControllerTest
================================================================================
```

---

### 3.4. `codecontext sarif`

#### What Does It Do?
The `sarif` command performs a full repository architectural analysis and exports the results in **OASIS SARIF 2.1.0 (Static Analysis Results Interchange Format)**.
* **GitHub Actions Native Integration:** Uploading this SARIF file via `github/codeql-action/upload-sarif@v3` enables **native inline annotations on GitHub Pull Request lines**!
* Maps circular dependencies to Rule ID **`CC001`** (level: `error`).
* Maps high PageRank and high churn hotspots to Rule ID **`CC002`** (level: `warning`).
* **POSIX Relative Path Normalization:** Formats all file locations as repository-relative forward-slash URIs (`src/main/java/com/example/MyClass.java`), ensuring cross-platform GitHub PR compatibility.

#### Command Syntax:
```bash
codecontext sarif [PATH] [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `[PATH]` | Argument | `.` *(Current Directory)* | Repository root directory to analyze. |
| `-o`, `--output <FILE>` | File Path | `codecontext.sarif` | File path where the SARIF JSON report will be saved. |
| `--include-tests` | Flag | `false` | Includes test classes in the analysis. |

---


---

### 3.5. `codecontext trace`

#### What Does It Do?
The `trace` command discovers and displays the **shortest directed method invocation chain** connecting any two classes or components in your architecture:
* It runs breadth-first traversal (BFS) over the dependency graph to find the minimal path from Source `<FROM>` to Target `<TO>`.
* It inspects invocation call-sites along the path, detailing the calling method, target method, and exact source file line numbers.
* It supports diamond architectures, multi-module paths, and guards against path explosion via an adjustable hop ceiling (`--max-hops`).
* If no directed path connects the two components, it reports an informative disconnected message and exits cleanly (`0`).

#### Command Syntax:
```bash
codecontext trace <FROM> <TO> [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `<FROM>` | Parameter | *(Required)* | Source starting class name (simple name like `OrderController` or FQCN). |
| `<TO>` | Parameter | *(Required)* | Target destination class name (simple name like `PaymentGateway` or FQCN). |
| `--path <DIR>` | Path | `.` *(Current Directory)* | Repository root directory to analyze. |
| `--max-hops <N>` | Integer | `10` | Maximum call-chain hops to search before terminating. |
| `--no-color` | Flag | `false` | Disables ANSI color escapes for clean terminal logs. |

---

#### What Happens: With vs. Without Options

##### 1. `--max-hops <N>` Option
* **Without `--max-hops` (Default: 10):** Searches for paths up to 10 hops deep.
* **With `--max-hops 2`:** Restricts search to direct or two-step connections; exits with `Path exists but exceeds maximum allowed hops` if only deeper paths exist.

##### 2. Class Resolution
* **Simple Names (`codecontext trace OrderController PaymentService`):** Fast and convenient; automatically resolves to fully-qualified names.
* **FQCNs (`codecontext trace com.example.OrderController com.example.PaymentService`):** Disambiguates identically-named classes across different packages.

#### Real Example Run:
```bash
codecontext trace AnalyzeCommand PageRankEngine
```
**Terminal Output:**
```text
================================================================================
 Execution Path Trace: AnalyzeCommand -> PageRankEngine (1 hop)
================================================================================
 Step 1: com.codecontext.cli.AnalyzeCommand
   +-- calls PageRankEngine.<init>() [Line 120]
   +-- calls PageRankEngine.compute() [Line 121]
 Final Target: com.codecontext.core.graph.analytics.PageRankEngine
================================================================================
```

---

### 3.6. `codecontext diff-impact`

#### What Does It Do?
The `diff-impact` command connects Git revision control directly to architectural intelligence:
* It inspects your **Git working tree (unstaged + staged changes)** or compares your current branch against a base ref (e.g. `origin/main` or `HEAD~1`).
* It maps modified files and lines directly to their corresponding AST classes and methods.
* It computes the **aggregate PR blast radius** across all modified classes, showing who depends on the code you changed.
* It detects circular dependencies on the modified codebase state, acting as an **automated CI/CD regression quality gate** (exits `1` on cycles).
* It recommends the exact unit and integration test suites that need to be run based on your changes.
* It generates a structured **GitHub Pull Request Markdown Summary Table** that can be directly posted as a PR comment or attached to `$GITHUB_STEP_SUMMARY`.

#### Command Syntax:
```bash
codecontext diff-impact [BASE_BRANCH] [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `[BASE_BRANCH]` | Parameter | *Empty (working tree)* | Base Git ref or branch to compare against (e.g. `origin/main`, `HEAD~1`). |
| `--path <DIR>` | Path | `.` *(Current Directory)* | Repository root directory to analyze. |
| `-d`, `--depth <N>` | Integer | `3` | Maximum traversal depth for transitive blast radius. |
| `-o`, `--output <FILE>` | File Path | *Stdout* | Saves the GitHub PR Markdown summary table to a file (e.g. `pr-summary.md`). |
| `--fail-on-cycles` | Flag | `true` | Fails the build with exit code `1` if circular dependencies exist or were introduced. |
| `--no-color` | Flag | `false` | Disables ANSI coloring. |

---

#### What Happens: With vs. Without Options

##### 1. `[BASE_BRANCH]` Parameter
* **Without `[BASE_BRANCH]` (`codecontext diff-impact`):** Inspects uncommitted local changes in your working tree (unstaged + staged files).
* **With `[BASE_BRANCH]` (`codecontext diff-impact origin/main`):** Compares your branch against `origin/main` to analyze the entire pull request.

##### 2. `--output <FILE>` Option
* **Without `--output`:** Prints the Markdown table directly to stdout.
* **With `--output pr-summary.md`:** Prints to stdout AND writes `pr-summary.md` to disk, ready for `actions/github-script` or PR commenting tools.

##### 3. `--fail-on-cycles` Option
* **Default (`true`):** If any circular dependency exists in the modified repository state, returns exit code `1` (`VIOLATION`) to block pull requests.
* **`--fail-on-cycles=false`:** Reports cycles in the table but returns exit code `0` for advisory monitoring.

#### Real Example Run:
```bash
codecontext diff-impact origin/main -o pr-summary.md
```
**Generated Markdown Output:**
```markdown
### CodeContext Pull Request Blast Radius & Architecture Check

| Metric | Value |
|---|---|
| **Status** | Passed (Clean) |
| **Modified Classes Touched** | `2` |
| **Total Impacted Callers (Blast Radius)** | `5` |
| **Architectural Cycles** | `0` (Clean) |

#### Touched Classes & Blast Radius
- `com.example.service.OrderService`
  - Direct Callers (In-degree: 1)
  - Transitive Blast Radius: `4` callers

#### Recommended Test Scope
- `com.example.service.OrderServiceTest`
- `com.example.controller.OrderControllerTest`
```

---

### 3.7. `codecontext prepare`

#### What Does It Do?
The `prepare` command implements advisory **EARS (Easy Approach to Requirements Syntax)** change-safety contracts for architecture teams:
* Before refactoring a critical core class, run `prepare` to synthesize a contract manifest (`.codecontext-plan.json`).
* It automatically identifies the target class's file location, matching test suites, and all upstream callers.
* It synthesizes deterministic EARS behavioral rules:
  - *WHEN modifying `<Target>`, THE SYSTEM SHALL NOT break public API signatures.*
  - *WHILE modifying `<Target>`, ONLY files in `allowedModifiedFiles` SHALL be changed.*
  - *IF `<Target>` has callers, THE SYSTEM SHALL preserve behavioral contracts.*

#### Command Syntax:
```bash
codecontext prepare --target <TARGET_CLASS> [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `-t`, `--target <CLASS>` | String | *(Required)* | Target class name to prepare change contract for. |
| `--path <DIR>` | Path | `.` *(Current Directory)* | Repository path to analyze. |
| `-o`, `--output <FILE>` | File Path | `.codecontext-plan.json` | Path where plan manifest JSON should be saved. |

#### Real Example Run:
```bash
codecontext prepare --target OrderService
```
**Terminal Output:**
```text
Advisory EARS change-safety plan generated: .codecontext-plan.json
Target Class: com.example.service.OrderService (Allowed files: 2, EARS rules: 3)
```

---

### 3.8. `codecontext verify`

#### What Does It Do?
The `verify` command enforces the boundaries declared in a `.codecontext-plan.json` manifest:
* It inspects current Git uncommitted changes in your working tree.
* It checks every modified file against the `allowedModifiedFiles` list in the plan.
* **If only allowed files were modified:** Returns exit code `0` (`SUCCESS`).
* **If unauthorized files were touched (Scope Creep):** Reports exact file violations and returns exit code `1` (`VIOLATION`).

#### Command Syntax:
```bash
codecontext verify [OPTIONS]
```

#### Available Options & Flags:
| Option / Flag | Type | Default Value | Plain English Description |
| :--- | :---: | :---: | :--- |
| `-p`, `--plan <FILE>` | File Path | `.codecontext-plan.json` | Path to the change safety plan to verify against. |
| `--path <DIR>` | Path | `.` *(Current Directory)* | Repository root path. |

#### Real Example Run:
```bash
codecontext verify --plan .codecontext-plan.json
```
**Terminal Output (On Scope Violation):**
```text
Scope violation: The following modified files are outside the authorized plan scope:
  [VIOLATION] src/main/java/com/example/billing/PaymentService.java
```
*Process exits with code `1`.*

---
## 4. Real-World CI/CD Automation (Working Today)

Because `codecontext` adheres to standard exit codes and outputs standard SARIF, you can integrate it into your continuous integration pipelines today.

### Example: GitHub Actions Workflow (`.github/workflows/architecture-check.yml`)

This production-ready workflow runs on every pull request:
1. Breaks the build if any new architectural cycle was introduced (`cycles --fail-on-cycles`).
2. Generates the interactive HTML Visual Blueprint and saves it as a downloadable CI artifact (`analyze`).
3. Emits SARIF 2.1.0 and posts inline code warnings directly on the GitHub PR diff (`sarif`).

```yaml
name: Architecture Governance Check

on:
  pull_request:
    branches: [ main ]
  push:
    branches: [ main ]

jobs:
  codecontext-gate:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build CodeContext CLI
        run: ./gradlew :codecontext-cli:installDist

      # 1. Quality Gate: Fails the build if circular dependencies exist
      - name: Enforce Zero Circular Dependencies
        run: ./codecontext-cli/build/install/codecontext/bin/codecontext cycles . --fail-on-cycles

      # 2. Generate Interactive Architecture Blueprint Report
      - name: Generate Architecture Blueprint Report
        run: ./codecontext-cli/build/install/codecontext/bin/codecontext analyze . --output ./build/reports

      # 3. Upload Visual HTML Report as CI Artifact
      - name: Upload Architecture Report
        uses: actions/upload-artifact@v4
        with:
          name: codecontext-architecture-blueprint
          path: ./build/reports/codecontext-graph.html

      # 4. Generate SARIF and post inline PR annotations
      - name: Generate SARIF Findings
        run: ./codecontext-cli/build/install/codecontext/bin/codecontext sarif . --output codecontext.sarif

      - name: Upload SARIF to GitHub Code Scanning
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: codecontext.sarif
```

---

## 5. Guide for Repository Readers: Understanding Architecture Metrics

When reading CodeContext outputs in the terminal or in `codecontext-graph.html`, here is what the technical metrics mean:

| Metric | Plain English Meaning | Why It Matters to Engineers |
| :--- | :--- | :--- |
| **In-Degree (Afferent Coupling)** | The number of other classes that directly call or import this class. | High in-degree means many components depend on you. Changes here can cause widespread ripple effects. |
| **Out-Degree (Efferent Coupling)** | The number of external classes this class depends on. | High out-degree means this class has many responsibilities and is vulnerable to changes in its dependencies. |
| **PageRank Authority** | A recursive mathematical score indicating how central a class is. It measures not just *how many* classes depend on it, but *how important* those callers are. | Identifies hidden architectural bottlenecks. A class with moderate in-degree that is called by the application's core controllers gets a very high PageRank score. |
| **Blast Radius** | The total set of components (direct + transitive callers) impacted if you modify this class. | Tells you the true scope of testing required before deploying a refactoring. |
| **Architectural Cycle** | A circular path of dependencies (`A -> B -> C -> A`). | Mutual dependencies make independent testing and library extraction impossible. CodeContext highlights the weakest link to break the cycle. |

---

## 6. Summary Cheat Sheet

| I Want To... | Exact Command To Run | Exit Code on Success |
| :--- | :--- | :---: |
| See all available commands & options | `codecontext --help` | `0` |
| Check the version | `codecontext --version` | `0` |
| Scan project & generate interactive HTML report | `codecontext analyze .` | `0` |
| Save HTML report in a custom folder | `codecontext analyze . --output /path/to/folder` | `0` |
| Get architecture statistics in machine JSON | `codecontext analyze . --json` | `0` |
| Run quietly (print only the report path) | `codecontext analyze . -q` | `0` |
| List all circular dependencies without failing | `codecontext cycles .` | `0` |
| Break CI if circular dependencies exist | `codecontext cycles . --fail-on-cycles` | `0` *(or `1` if cycles exist)* |
| Check blast radius before touching a class | `codecontext impact <ClassName>` | `0` |
| Suggest test suites to run for impacted code | `codecontext impact <ClassName> --suggest-tests` | `0` |
| Get blast radius as machine JSON | `codecontext impact <ClassName> --json` | `0` |
| Generate SARIF report for GitHub PR annotations | `codecontext sarif . --output results.sarif` | `0` |
| Trace shortest call chain between two classes | `codecontext trace <From> <To>` | `0` |
| Cap call chain search hops | `codecontext trace <From> <To> --max-hops 5` | `0` |
| Analyze PR blast radius & check cycle regression | `codecontext diff-impact origin/main` | `0` *(or `1` on cycles)* |
| Save PR blast radius summary to Markdown file | `codecontext diff-impact origin/main -o pr-summary.md` | `0` *(or `1` on cycles)* |
| Synthesize advisory EARS change safety contract | `codecontext prepare --target <ClassName>` | `0` |
| Verify working tree changes against plan scope | `codecontext verify --plan .codecontext-plan.json` | `0` *(or `1` on breach)* |

*(All 8 commands listed above are 100% implemented, tested, and active in CodeContext v2.0).*
