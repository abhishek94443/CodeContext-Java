
> Correctness clarification: historical counts and claims below describe earlier runs or design intentions. The production scanner does not use StringFlyweightPool. Historical Gson values 722 and 1,333 are total graph vertices (including boundary vertices) and aggregated directed edges, not source classes or call occurrences. The 12 reported paths were truncated cycle examples, not complete enumeration or an SCC count. Graph paths are class-level reachability, not guaranteed runtime execution. Only SCC computation is linear; cycle enumeration and BFS have the documented limits, not fixed resource guarantees. CBOM schema tests cover particular fixtures and do not prove all semantic mappings. See [current correctness report](../CORRECTNESS_REMEDIATION.md).

# CodeContext Java — Commands Executed Log & Artifact Mapping

**Audit Date:** 2026-11-14  
**Audit Target:** Merged `main` branch (`9cc5193`)  
**Auditor Role:** Independent Technical Auditor  

---

## 1. Audit Command Log

| Step # | Exact Command Executed | Exit Code | Purpose / Operation | Generated Output Artifact(s) |
|---|---|---|---|---|
| **01** | `git status` | `0` | Audit Git working tree state | Verified clean working tree |
| **02** | `git branch --show-current` | `0` | Confirm active branch | Verified `main` |
| **03** | `git rev-parse HEAD` | `0` | Identify HEAD commit SHA | `9cc5193a9927ec91f1e8bf41544868cbd78fd081` |
| **04** | `git show -s --format=fuller 9cc5193` | `0` | Audit merge commit parents | Parent 1: `6299839`, Parent 2: `aa6c662` |
| **05** | `git diff --check` | `0` | Check for unresolved conflict markers | Zero conflict markers detected |
| **06** | `java -version` | `0` | Environment runtime verification | Java 21.0.3 LTS (Oracle 64-Bit Server VM) |
| **07** | `./gradlew --version` | `0` | Gradle version verification | Gradle 9.1.0 |
| **08** | `./gradlew clean test --rerun-tasks` | `0` | Full clean build & regression suite | 282 tests passing (210 core, 72 CLI), 0 failures |
| **09** | `./gradlew :codecontext-cli:run --args="--help" -q` | `0` | Root CLI subcommand discovery | Help menu listing all 10 root commands |
| **10** | `./gradlew :codecontext-cli:run --args="analyze C:\...\gson --output audit-output/gson --json" -q > audit-output/gson/architecture-summary.json` | `0` | Full architecture analysis on Gson | `audit-output/gson/architecture-summary.json`<br>`audit-output/gson/codecontext-graph.html` (235 KB) |
| **11** | `./gradlew :codecontext-cli:run --args="cycles C:\...\gson --json" -q > audit-output/gson/gson-cycles.json` | `0` | Circular dependency detection on Gson | `audit-output/gson/gson-cycles.json` (12 cycles) |
| **12** | `./gradlew :codecontext-cli:run --args="impact com.google.gson.Gson --path C:\...\gson --json" -q > audit-output/gson/gson-impact-gson.json` | `0` | Class blast-radius calculation on Gson | `audit-output/gson/gson-impact-gson.json` (128 callers) |
| **13** | `./gradlew :codecontext-cli:run --args="trace com.google.gson.Gson com.google.gson.stream.JsonReader --path C:\...\gson" -q` | `0` | 1-hop shortest path execution trace | Console output (1-hop call chain, line 822) |
| **14** | `./gradlew :codecontext-cli:run --args="trace com.google.gson.JsonParser com.google.gson.stream.JsonWriter --path C:\...\gson" -q` | `0` | 2-hop shortest path execution trace | Console output (2-hop call chain via JsonElement) |
| **15** | `./gradlew :codecontext-cli:run --args="sarif C:\...\gson -o C:\...\audit-output\gson\codecontext.sarif" -q` | `0` | OASIS SARIF 2.1.0 architectural export | `audit-output/gson/codecontext.sarif` (13 findings) |
| **16** | `./gradlew :codecontext-cli:run --args="prepare -t com.google.gson.Gson --path C:\...\gson -o C:\...\audit-output\gson\gson-plan.json" -q` | `0` | Synthesize advisory EARS change plan | `audit-output/gson/gson-plan.json` (2 allowed files) |
| **17** | `./gradlew :codecontext-cli:run --args="verify -p C:\...\gson-plan.json --path C:\...\gson" -q` | `1` (Expected) | Change-safety verification gate | Correctly caught untracked scope violations |
| **18** | `./gradlew :codecontext-cli:run --args="diff-impact HEAD~1 --path C:\...\gson --fail-on-cycles=false -o C:\...\diff-impact-summary.md" -q` | `0` | Git diff PR blast radius on Gson | `audit-output/gson/diff-impact-summary.md` (69 classes touched, 224 callers impacted) |
| **19** | Chrome DevTools MCP navigation to `audit-output/gson/codecontext-graph.html` | `0` | Browser evaluation of architecture HTML | Verified rendering, 186 classes, 12 cycles, 0 console errors |
| **20** | `./gradlew :codecontext-cli:run --args="security scan C:\...\BenchmarkJava --format json -o C:\...\security-summary.json" -q` | `0` | Security scan on BenchmarkJava (JSON) | `audit-output/benchmark/security-summary.json` (210 findings) |
| **21** | `./gradlew :codecontext-cli:run --args="security scan C:\...\BenchmarkJava -o C:\...\codecontext-security.html" -q` | `0` | Generate security HTML dashboard | `audit-output/benchmark/codecontext-security.html` (1.09 MB) |
| **22** | `./gradlew :codecontext-cli:run --args="crypto inventory C:\...\BenchmarkJava --format json -o C:\...\crypto-inventory.json" -q` | `0` | Cryptographic inventory on BenchmarkJava | `audit-output/benchmark/crypto-inventory.json` (16 assets) |
| **23** | `./gradlew :codecontext-cli:run --args="crypto cbom C:\...\BenchmarkJava -o C:\...\crypto-bom.json" -q` | `0` | CycloneDX 1.6 CBOM export | `audit-output/benchmark/crypto-bom.json` (16 components) |
| **24** | Scratch Java runner validating `crypto-bom.json` against `bom-1.6.schema.json` | `0` | Official CBOM JSON schema validation | `VALIDATION_ERROR_COUNT=0` (0 schema errors) |
| **25** | `./gradlew :codecontext-cli:run --args="crypto pqc C:\...\BenchmarkJava --format json -o C:\...\pqc-assessment.json" -q` | `0` | PQC migration assessment on BenchmarkJava | `audit-output/benchmark/pqc-assessment.json` (16 assets) |
| **26** | Chrome DevTools MCP navigation to `audit-output/benchmark/codecontext-security.html` | `0` | Browser evaluation of security HTML | Verified 6 tabs, PQC cards, inventory, 0 console errors |
| **27** | `reconcile_benchmark.ps1` | `0` | Reconcile findings vs `expectedresults-1.2.csv` | `audit-output/benchmark/benchmark-scoring-reconciliation.csv` |
| **28** | `benchmark_performance.ps1` | `0` | Measure accurate execution times across repeated runs | `audit-output/benchmark/performance-measurements.csv` |
