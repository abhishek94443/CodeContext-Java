
> Correctness clarification: historical counts and claims below describe earlier runs or design intentions. The production scanner does not use StringFlyweightPool. Historical Gson values 722 and 1,333 are total graph vertices (including boundary vertices) and aggregated directed edges, not source classes or call occurrences. The 12 reported paths were truncated cycle examples, not complete enumeration or an SCC count. Graph paths are class-level reachability, not guaranteed runtime execution. Only SCC computation is linear; cycle enumeration and BFS have the documented limits, not fixed resource guarantees. CBOM schema tests cover particular fixtures and do not prove all semantic mappings. See [current correctness report](../CORRECTNESS_REMEDIATION.md).

# CodeContext Java — Complete Supported Feature & CLI Inventory

**Audit Date:** 2026-11-14  
**Audit Target:** Merged `main` branch (`9cc5193`)  
**Auditor Role:** Principal Java Engineer & Independent Technical Auditor  

---

## 1. Feature Verification Matrix

| # | Feature / Capability | CLI Command | Implementation Class | Primary Input | Primary Output | Verification Evidence | Status | Known Limitations |
|---|---|---|---|---|---|---|---|---|
| **1** | **Full Repository Architectural Analysis** | `codecontext analyze <path> [--output <dir>] [--json]` | `AnalyzeCommand.java` | Java source directory | `codecontext-graph.html` (interactive visual graph) or JSON summary | Ran on Gson (722 classes, 1333 invocations, 12 cycles); generated 235 KB standalone HTML | **VERIFIED** | Default excludes test files unless `--include-tests` is passed |
| **2** | **Circular Dependency Loop Detection** | `codecontext cycles <path> [--json] [--fail-on-cycles]` | `CyclesCommand.java` | Java source directory | Terminal report or JSON array of cycle paths | Ran on Gson: detected 12 bounded cycles; verified Kosaraju SCC engine | **VERIFIED** | Picocli description says Tarjan; actual engine uses Kosaraju SCC with bounded cycle extraction |
| **3** | **Class Blast Radius & Caller Impact** | `codecontext impact <class> --path <path> [-d <depth>] [--json]` | `ImpactCommand.java` | Target FQCN / simple name & repo path | Direct & transitive callers with traversal depth, blast radius, risk score | Ran on `com.google.gson.Gson`: in-degree 123, total blast radius 128 callers, risk 0.121 | **VERIFIED** | Class-level dependency reachability, not method-level execution path |
| **4** | **Execution Path Tracing** | `codecontext trace <from> <to> --path <path> [--max-hops <n>]` | `TraceCommand.java` | Source and Target class names | Shortest call chain with method names and call-site source lines | Ran on Gson: `JsonParser -> JsonWriter` (2 hops: line 111, line 424, line 427) | **VERIFIED** | Traverses static call graph; reflection/dynamic proxies not traced |
| **5** | **Pull Request Diff Blast Radius** | `codecontext diff-impact <baseRef> --path <path> [-d <depth>] [-o <file>]` | `DiffImpactCommand.java` | Git base branch/ref & working tree | Markdown summary of modified classes, blast radius, cycles, and test scope | Ran on Gson (`HEAD~1`): 69 classes touched, 224 callers impacted, 12 cycles, 87 recommended tests | **VERIFIED** | Requires local Git repository history |
| **6** | **Advisory EARS Safety Plan Synthesis** | `codecontext prepare -t <targetClass> --path <path> -o <plan.json>` | `PrepareCommand.java` | Target class & repository path | JSON EARS change-safety plan with authorized scope & safety constraints | Ran on Gson (`Gson`): generated plan with 2 allowed files & 3 EARS rules | **VERIFIED** | Advisory specification intended for agentic guardrails |
| **7** | **Safety Plan Verification Gate** | `codecontext verify -p <plan.json> --path <path>` | `VerifyCommand.java` | EARS JSON plan & working tree | Scope verification report; exit code 0 if compliant, 1 if violations found | Ran on Gson: detected untracked files outside plan scope, returned exit code 1 | **VERIFIED** | Verifies file path scope against git status modifications |
| **8** | **OASIS SARIF 2.1.0 Export** | `codecontext sarif <path> -o <file.sarif>` | `SarifCommand.java` | Java source directory | OASIS SARIF 2.1.0 JSON file | Ran on Gson: exported 13 findings to `codecontext.sarif` | **VERIFIED** | Emits architectural cycle and hotspot findings |
| **9** | **Cryptographic Security Scanning** | `codecontext security scan <path> [-o <file>] [--format <table\|json\|sarif\|html>]` | `SecurityScanCommand.java` | Java source directory | Findings table, JSON, SARIF 2.1.0, or standalone offline security HTML dashboard | Ran on BenchmarkJava: 210 findings, 1.09 MB offline HTML dashboard, 6 tabs | **VERIFIED** | AST-based expression resolution; properties files are flagged as UNKNOWN |
| **10** | **Cryptographic Asset Inventory** | `codecontext crypto inventory <path> [-o <file>] [--format <text\|json>]` | `CryptoInventoryCommand.java` | Java source directory | Normalized cryptographic asset inventory | Ran on BenchmarkJava: 1,143 occurrences, 16 unique assets normalized | **VERIFIED** | Discloses confidence level for dynamically resolved values |
| **11** | **CycloneDX 1.6 CBOM Export** | `codecontext crypto cbom <path> -o <file.json>` | `CryptoCbomCommand.java` | Java source directory | CycloneDX 1.6 Cryptography Bill of Materials (CBOM) JSON | Ran on BenchmarkJava: exported 16 components, 0 schema errors against official schema | **VERIFIED** | Follows CycloneDX 1.6 CBOM specification; RSA mapped to `pke` |
| **12** | **Post-Quantum Migration Assessment** | `codecontext crypto pqc <path> [-o <file>] [--format <table\|json>] [--max-depth <n>]` | `CryptoPqcCommand.java` | Java source directory | NIST PQC exposure classification, migration guidance, and call-graph blast radius | Ran on BenchmarkJava: 2 PQC candidates, 4 classical remediations, 6 quantum considerations, 4 unknown | **VERIFIED** | Requires cryptographic context to recommend parameter sets |

---

## 2. Copy-Pasteable Windows PowerShell Execution Commands

### Architecture Intelligence Commands
```powershell
# 1. Full repository architecture analysis and HTML graph generation
./gradlew :codecontext-cli:run --args="analyze C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson --output audit-output/gson --json" -q

# 2. Cycle detection with fail-on-cycles check
./gradlew :codecontext-cli:run --args="cycles C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson --json" -q

# 3. Class impact and blast radius
./gradlew :codecontext-cli:run --args="impact com.google.gson.Gson --path C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson -d 3 --json" -q

# 4. Shortest call path execution trace
./gradlew :codecontext-cli:run --args="trace com.google.gson.JsonParser com.google.gson.stream.JsonWriter --path C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson" -q

# 5. Git PR diff blast radius analysis
./gradlew :codecontext-cli:run --args="diff-impact HEAD~1 --path C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson --fail-on-cycles=false -o audit-output/gson/diff-impact.md" -q

# 6. Synthesize EARS change-safety plan
./gradlew :codecontext-cli:run --args="prepare -t com.google.gson.Gson --path C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson -o audit-output/gson/gson-plan.json" -q

# 7. Verify changes against change-safety plan
./gradlew :codecontext-cli:run --args="verify -p audit-output/gson/gson-plan.json --path C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson" -q

# 8. Export architectural findings to SARIF 2.1.0
./gradlew :codecontext-cli:run --args="sarif C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson -o C:\Users\Lenovo\Desktop\abhishe\shivansh-codecontext\codecontext-java\audit-output\gson\codecontext.sarif" -q
```

### Cryptographic Security & Post-Quantum Intelligence Commands
```powershell
# 9. Cryptographic security scan with standalone offline HTML dashboard
./gradlew :codecontext-cli:run --args="security scan C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava -o C:\Users\Lenovo\Desktop\abhishe\shivansh-codecontext\codecontext-java\audit-output\benchmark\codecontext-security.html" -q

# 10. Cryptographic security scan in JSON format
./gradlew :codecontext-cli:run --args="security scan C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava --format json -o C:\Users\Lenovo\Desktop\abhishe\shivansh-codecontext\codecontext-java\audit-output\benchmark\security-summary.json" -q

# 11. Extract normalized cryptographic inventory
./gradlew :codecontext-cli:run --args="crypto inventory C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava --format json -o C:\Users\Lenovo\Desktop\abhishe\shivansh-codecontext\codecontext-java\audit-output\benchmark\crypto-inventory.json" -q

# 12. Export CycloneDX 1.6 Cryptography Bill of Materials (CBOM)
./gradlew :codecontext-cli:run --args="crypto cbom C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava -o C:\Users\Lenovo\Desktop\abhishe\shivansh-codecontext\codecontext-java\audit-output\benchmark\crypto-bom.json" -q

# 13. Evaluate Post-Quantum Cryptography (PQC) exposure and call-graph blast radius
./gradlew :codecontext-cli:run --args="crypto pqc C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava --format json -o C:\Users\Lenovo\Desktop\abhishe\shivansh-codecontext\codecontext-java\audit-output\benchmark\pqc-assessment.json" -q
```
