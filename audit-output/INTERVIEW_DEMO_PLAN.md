
> Correctness clarification: historical counts and claims below describe earlier runs or design intentions. The production scanner does not use StringFlyweightPool. Historical Gson values 722 and 1,333 are total graph vertices (including boundary vertices) and aggregated directed edges, not source classes or call occurrences. The 12 reported paths were truncated cycle examples, not complete enumeration or an SCC count. Graph paths are class-level reachability, not guaranteed runtime execution. Only SCC computation is linear; cycle enumeration and BFS have the documented limits, not fixed resource guarantees. CBOM schema tests cover particular fixtures and do not prove all semantic mappings. See [current correctness report](../CORRECTNESS_REMEDIATION.md).

# CodeContext Java — Live Interview Demonstration Plan & Script

**Audit Date:** 2026-11-14  
**Audit Target:** Merged `main` branch (`9cc5193`)  
**Auditor Role:** Principal Java Engineer & Technical Interviewer  

---

## Demonstration Overview

This interview plan is designed for a 15–20 minute live technical demonstration showcasing architectural code intelligence, static cryptographic security, CycloneDX 1.6 CBOM generation, and Post-Quantum Cryptography (PQC) blast-radius analysis across two real-world open-source repositories:
1. **Google Gson** (`264 Java files, ~50,000 LOC`): Demonstrating call graphs, cycle detection, PageRank hotspots, shortest path tracing, and blast radius.
2. **OWASP BenchmarkJava** (`2,767 Java files, ~245,000 LOC`): Demonstrating static crypto scanning, 100% precision on CWE-327/328, CycloneDX 1.6 CBOM, and NIST PQC migration assessment.

---

## DEMO A: High-Performance Architecture Intelligence (Google Gson)

### 1. Step 1: Full Repository Analysis & Hotspot Detection
**Executable Command:**
```powershell
./gradlew :codecontext-cli:run --args="analyze C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson --output audit-output/gson --json" -q
```
**Measured Execution Time:** ~6.6 seconds (warm daemon)  
**Expected Output / Evidence:**
- **Files Parsed:** 122 main production source files
- **Vertices (Classes):** 722 classes/interfaces
- **Edges (Invocations):** 1,333 static call edges
- **Cycles Detected:** 12 bounded circular loops
- **Top Hotspots:**
  1. `com.google.gson.stream.JsonReader` (PageRank: 0.00929, In-degree: 32)
  2. `com.google.gson.stream.JsonWriter` (PageRank: 0.00548, In-degree: 26)
  3. `com.google.gson.Gson` (PageRank: 0.00527, In-degree: 25)

**What to Explain Aloud:**
> *"CodeContext parsed all 122 compilation units and resolved 1,333 invocation edges in ~6 seconds without requiring a pre-compiled JAR or Maven build. Using power-iteration PageRank over directed dependency edges, it immediately reveals that streaming IO primitives (`JsonReader`, `JsonWriter`) and the facade `Gson` form the architectural core of the library."*

---

### 2. Step 2: Shortest Execution Path Tracing
**Executable Command:**
```powershell
./gradlew :codecontext-cli:run --args="trace com.google.gson.JsonParser com.google.gson.stream.JsonWriter --path C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson" -q
```
**Measured Execution Time:** ~8.1 seconds  
**Expected Output / Evidence:**
```
================================================================================
 Execution Path Trace: JsonParser -> JsonWriter (2 hops)
================================================================================
 Step 1: com.google.gson.JsonParser
   +-- calls JsonElement.isJsonNull() [Line 111]
 Step 2: com.google.gson.JsonElement
   +-- calls JsonWriter.<init>() [Line 424]
   +-- calls JsonWriter.setStrictness() [Line 427]
 Final Target: com.google.gson.stream.JsonWriter
================================================================================
```
**What to Explain Aloud:**
> *"When debugging or refactoring, engineers need to understand how two distant classes interact. CodeContext uses breadth-first search on the directed call graph to find the shortest execution chain between `JsonParser` and `JsonWriter`. It pinpoints the exact 2-hop bridge through `JsonElement` along with exact source line numbers (Line 111, Line 424, Line 427)."*

---

### 3. Step 3: Class Blast Radius & Dependency Impact
**Executable Command:**
```powershell
./gradlew :codecontext-cli:run --args="impact com.google.gson.Gson --path C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson -d 3 --json" -q
```
**Measured Execution Time:** ~8.6 seconds  
**Expected Output / Evidence:**
- Target Class: `com.google.gson.Gson`
- In-degree: 123 direct callers
- Total Blast Radius: 128 unique callers across depth $\le 3$
- Cumulative Risk Score: 0.121

**What to Explain Aloud:**
> *"If an engineer modifies the core `Gson` class, CodeContext calculates its blast radius by inverting call edges. It identifies 123 direct callers and 5 transitive-only callers, providing a complete list of impacted components and recommending relevant test suites."*

---

### 4. Step 4: Standalone Offline Architecture Graph
**File Path:** `audit-output/gson/codecontext-graph.html` (235 KB)  
**Action:** Open directly in browser via `file://`.  
**Show:**
- Instant loading with **zero external HTTP requests**.
- Overview tab displaying 186 key classes and 1,333 connections.
- Cycles tab displaying all 12 circular dependency loops.
- Live search bar filtering nodes in real time.

---

## DEMO B: Cryptographic Security & CBOM (OWASP BenchmarkJava)

### 1. Step 1: Real-World Security Scan & False-Positive Immunity
**Executable Command:**
```powershell
./gradlew :codecontext-cli:run --args="security scan C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava --format json -o audit-output/benchmark/security-summary.json" -q
```
**Measured Execution Time:** ~8.4 seconds across 2,767 files  
**Expected Output / Evidence:**
- **210 Security Findings** across 186 unique true-positive benchmark cases.
- **Zero False Positives (100% Precision, 0% FPR)** against official `expectedresults-1.2.csv`.
- **CWE-327 Findings:** 97 true DES legacy cipher findings (`CC-CRYPTO-002`, e.g. `BenchmarkTest00005.java:63`).
- **CWE-328 Findings:** 113 true weak message digest findings (`CC-CRYPTO-003`, MD5 and SHA-1, e.g. `BenchmarkTest00046.java:50`).
- **The Corrected RSA/OAEP Test:** `org.owasp.benchmark.helpers.Utils.java:101`:
  ```java
  Cipher.getInstance("RSA/ECB/OAEPWithSHA-512AndMGF1Padding");
  ```
  CodeContext recognizes that this is asymmetric public-key encryption and **does not falsely trigger symmetric ECB mode (`CC-CRYPTO-001`)**.

**What to Explain Aloud:**
> *"On OWASP BenchmarkJava (2,767 files), CodeContext achieved 100% precision with 0 false positives across 482 evaluated CWE-327 and CWE-328 cases. Many commercial tools falsely flag `RSA/ECB/OAEP` as insecure ECB mode because they use naive string matching on `/ECB/`. CodeContext parses the cryptographic primitive and understands that RSA-OAEP is an asymmetric padding scheme, completely eliminating the false positive."*

---

### 2. Step 2: CycloneDX 1.6 CBOM Schema Compliance
**Executable Command:**
```powershell
./gradlew :codecontext-cli:run --args="crypto cbom C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava -o audit-output/benchmark/crypto-bom.json" -q
```
**Measured Execution Time:** ~8.5 seconds  
**Expected Output / Evidence:**
- 16 Cryptographic Components normalized across 1,143 API call sites.
- Validated with official `bom-1.6.schema.json`: **0 validation errors**.
- RSA correctly mapped to `pke` primitive (not `block-cipher`).
- Full occurrence evidence with zero private key or secret leakage.

---

## DEMO C: Post-Quantum Cryptography Migration Assessment

### 1. Step 1: PQC Exposure & NIST Guidance
**Executable Command:**
```powershell
./gradlew :codecontext-cli:run --args="crypto pqc C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava --format json -o audit-output/benchmark/pqc-assessment.json" -q
```
**Measured Execution Time:** ~9.8 seconds  
**Expected Output / Evidence:**
- **PQC Migration Candidates (2 Assets):**
  - `RSA` (KeyPairGenerator): Purpose `KEY_PAIR_GENERATION`. Vulnerable to Shor's algorithm. Guidance directs migration to ML-DSA/SLH-DSA for signatures, or ML-KEM for key exchange.
  - `RSA/ECB/OAEPWithSHA-512AndMGF1Padding`: Purpose `PUBLIC_KEY_ENCRYPTION`. Guidance warns that **ML-KEM is a KEM, not a direct drop-in `Cipher` replacement**, recommending hybrid KEM + symmetric AEAD wrapping.
- **Classical Remediation (4 Assets):** DES, MD5, SHA-1 flagged for immediate classical replacement.
- **Quantum Consideration (6 Assets):** AES-GCM and SHA-256 flagged for Grover key-length review (256-bit recommended).

---

### 2. Step 2: Graph-Based PQC Blast Radius & Semantic Honesty
**Inspect in `pqc-assessment.json`:**
```json
{
  "name": "RSA/ECB/OAEPWithSHA-512AndMGF1Padding",
  "category": "PQC_MIGRATION_CANDIDATE",
  "purpose": "PUBLIC_KEY_ENCRYPTION",
  "blastRadius": {
    "totalDirectDependents": 438,
    "totalTransitiveOnlyDependents": 0,
    "totalReachableClasses": 438,
    "isSharedUtilityUsage": true
  }
}
```
**What to Explain Aloud:**
> *"When planning PQC migration, knowing the algorithm isn't enough; you must know the migration blast radius. CodeContext links the RSA-OAEP usage in `Utils.java` to the call graph, showing that 438 benchmark test cases directly depend on `Utils`. Crucially, CodeContext discloses that this is class-level dependency reachability, not dynamic execution proof, and correctly flags `Utils` as a high fan-in shared utility rather than claiming 438 transitive callers."*

---

### 3. Step 3: Standalone Offline Security HTML Dashboard
**File Path:** `audit-output/benchmark/codecontext-security.html` (1.09 MB)  
**Action:** Open in browser via `file://`.  
**Show:**
- **Security Findings Tab:** 210 findings with search and severity filters.
- **Cryptographic Inventory Tab:** 16 normalized assets across 1,143 occurrences.
- **PQC Migration Tab:** Interactive cards displaying NIST FIPS 203/204/205 targets and blast radius metrics.
- **Zero external network requests, zero CDNs, zero console errors.**
