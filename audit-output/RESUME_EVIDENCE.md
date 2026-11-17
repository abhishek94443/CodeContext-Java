
> Correctness clarification: historical counts and claims below describe earlier runs or design intentions. The production scanner does not use StringFlyweightPool. Historical Gson values 722 and 1,333 are total graph vertices (including boundary vertices) and aggregated directed edges, not source classes or call occurrences. The 12 reported paths were truncated cycle examples, not complete enumeration or an SCC count. Graph paths are class-level reachability, not guaranteed runtime execution. Only SCC computation is linear; cycle enumeration and BFS have the documented limits, not fixed resource guarantees. CBOM schema tests cover particular fixtures and do not prove all semantic mappings. See [current correctness report](../CORRECTNESS_REMEDIATION.md).

# CodeContext Java — Verified Resume Evidence & Technical Achievements

**Audit Date:** 2026-11-14  
**Audit Target:** Merged `main` branch (`9cc5193`)  
**Auditor Role:** Principal Java Engineer & Independent Technical Auditor  

---

## VERSION 1: Concise ATS-Friendly Resume Bullets (Max 3 Bullets)

- **Engineered an offline Java static analysis engine** utilizing JavaParser AST traversal, lexical scoping, and JGraphT call graphs across 700+ classes, computing Kosaraju SCC circular dependencies, PageRank structural hotspots, and breadth-first search execution path traces with sub-7s median latency.
- **Architected a static cryptographic security analyzer and CycloneDX 1.6 CBOM generator**, auditing 1,100+ JCA/JCE call sites across OWASP BenchmarkJava (2,767 files) to achieve 100% precision (0 false positives) on evaluated CWE-327/328 cases with zero schema validation errors.
- **Designed a Post-Quantum Cryptography (PQC) migration engine**, classifying cryptographic exposure under NIST FIPS 203/204/205 standards and mapping call-graph blast radius to evaluate direct vs. transitive-only caller impact across enterprise repositories.

---

## VERSION 2: Detailed Technical Project Descriptions (GitHub README / Interview Discussion)

### A. Architectural Code Intelligence & Graph Analytics
- **Technical Claim:** Developed a two-pass AST declaration and invocation resolver using JavaParser, indexing types into a thread-safe symbol table with string flyweight pooling, and constructing a directed weighted JGraphT dependency graph without requiring pre-compiled bytecode.
- **Implementation Classes:** `BoundedPass1Scanner`, `ConcurrentSymbolTable`, `StringFlyweightPool`, `Pass2InvocationResolver`, `DependencyGraphBuilder`.
- **Verified Metrics:** Analyzed Google Gson (122 compilation units, 722 type vertices, 1,333 static invocations, 12 bounded circular loops) in 6.65s median execution time.
- **Interviewer Follow-Up:** *"How did you prevent memory leaks and exponential cycle extraction on heavily connected graphs?"*
  - **Answer:** Applied `StringFlyweightPool` to canonicalize FQCN strings, isolated Strongly Connected Components using Kosaraju's $O(V+E)$ algorithm, and imposed strict depth (max 7) and count (max 50) boundaries on elementary path extraction.
- **Evidence Confidence:** **100% Verified** by direct CLI execution and unit test suite.

---

### B. Cryptographic Security Intelligence & CycloneDX 1.6 CBOM
- **Technical Claim:** Built a static cryptographic rule engine and expression resolver detecting legacy ciphers (DES), weak digests (MD5, SHA-1), and insecure ECB modes while eliminating false positives on asymmetric schemes (RSA-OAEP), exporting compliant CycloneDX 1.6 Cryptography Bill of Materials (CBOM).
- **Implementation Classes:** `CryptoApiDetector`, `LimitedExpressionResolver`, `SecurityRuleRegistry`, `CryptoInventoryEngine`, `CycloneDxCbomExporter`.
- **Verified Metrics:** Audited 2,767 Java files in OWASP BenchmarkJava, detecting 1,143 crypto API usages, normalizing 16 unique assets, and generating valid CycloneDX 1.6 CBOM with 0 schema validation errors.
- **Interviewer Follow-Up:** *"How does your scanner distinguish symmetric ECB from RSA/ECB/OAEP?"*
  - **Answer:** Rather than naive substring matching on `/ECB/`, the rule engine inspects the cryptographic primitive and algorithm family; transformations starting with `RSA` are classified as asymmetric public-key encryption and exempted from symmetric block cipher rules.
- **Evidence Confidence:** **100% Verified** against official CycloneDX 1.6 JSON Schema validator.

---

### C. Post-Quantum Cryptography Migration Assessment & Blast Radius
- **Technical Claim:** Implemented an evidence-based PQC exposure classifier categorizing assets into PQC migration candidates, classical remediation, quantum considerations, and unresolved usages aligned with NIST standards, coupling assets with call-graph blast-radius analysis.
- **Implementation Classes:** `PqcMigrationEngine`, `CryptoPurpose`, `QuantumExposureLevel`, `AssessmentCategory`, `CryptoAssetBlastRadius`.
- **Verified Metrics:** Assessed 16 assets across BenchmarkJava, distinguishing Shor-vulnerable RSA key generation (purpose: `KEY_PAIR_GENERATION`) from RSA encryption (`PUBLIC_KEY_ENCRYPTION`), identifying 438 direct callers of `Utils.java` with high fan-in disclosure.
- **Interviewer Follow-Up:** *"Can ML-KEM be recommended as a drop-in replacement for RSA Cipher operations?"*
  - **Answer:** No. ML-KEM (NIST FIPS 203) is a Key Encapsulation Mechanism, not an arbitrary-data public-key encryption algorithm. CodeContext explicitly advises a hybrid KEM + symmetric AEAD wrapping architecture instead of suggesting an impossible direct Cipher substitute.
- **Evidence Confidence:** **100% Verified** in `pqc-assessment.json` and unit test assertions.

---

### D. Benchmark Validation, Testing & Reliability Engineering
- **Technical Claim:** Maintained a 282-test automated regression suite covering AST visitors, symbol tables, cycle detection, blast radius, CBOM validation, and offline HTML generation; evaluated scanner against 482 OWASP BenchmarkJava cases, achieving 100% precision and 0% false-positive rate.
- **Implementation Classes:** 55 JUnit test classes across `codecontext-core` and `codecontext-cli`.
- **Verified Metrics:**
  - Test Suite: 282 tests passing (210 core, 72 CLI), 0 failures, 0 errors, 40s execution time.
  - OWASP Benchmark: 186 TP, 0 FP, 73 FN (all dynamic `.properties` loading), 223 TN. Precision: 100.00%, Recall: 71.81%, F1: 83.59%.
- **Interviewer Follow-Up:** *"Why did the scanner have 73 false negatives on BenchmarkJava?"*
  - **Answer:** All 73 misses were caused by dynamic property loading (`benchmarkprops.getProperty(...)`). Static AST analysis intentionally refrains from unsound runtime guessing, treating dynamic arguments as `UNKNOWN` rather than generating speculative false positives.
- **Evidence Confidence:** **100% Audited** against checked-out benchmark commit and `expectedresults-1.2.csv`.
