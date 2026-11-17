
> Correctness clarification: historical counts and claims below describe earlier runs or design intentions. The production scanner does not use StringFlyweightPool. Historical Gson values 722 and 1,333 are total graph vertices (including boundary vertices) and aggregated directed edges, not source classes or call occurrences. The 12 reported paths were truncated cycle examples, not complete enumeration or an SCC count. Graph paths are class-level reachability, not guaranteed runtime execution. Only SCC computation is linear; cycle enumeration and BFS have the documented limits, not fixed resource guarantees. CBOM schema tests cover particular fixtures and do not prove all semantic mappings. See [current correctness report](../CORRECTNESS_REMEDIATION.md).

# CodeContext Java — Comprehensive Technical & Interview Learning Guide

**Audit Date:** 2026-11-14  
**Audit Target:** Merged `main` branch  
**Auditor Role:** Principal Java Engineer & Systems Architect  

---

## Module 1: Two-Pass Static Analysis & JavaParser AST

### 1. Problem Solved
Analyzing large Java repositories without requiring a fully compiled binary classpath (`.class` or `.jar` files) or invoking a slow compiler instance (e.g. `javac`). Enables instant static analysis directly from raw source code.

### 2. Beginner-Friendly Explanation
Imagine reading a book where Chapter 1 references a character introduced in Chapter 10. If you try to understand who that character is on page 1, you can't. In CodeContext, **Pass 1** skims the entire book to write down every character's name and address (declarations and packages). In **Pass 2**, it reads the book again, and now every time a character talks to someone, it knows exactly who they are (invocations and call graphs).

### 3. Entry Point & Core Classes
- **Entry Point:** `BoundedPass1Scanner.java`
- **Core Processing:** `JavaParserDeclarationVisitor.java`, `Pass2InvocationResolver.java`, `SourceFileFinder.java`
- **Data Models:** `ParsedCompilationUnit.java`, `TypeDefinition.java`, `ConcurrentSymbolTable.java`, `StringFlyweightPool.java`

### 4. Input & Output
- **Input:** File system path to Java source root (`Path repoPath`).
- **Output:** Populated `ConcurrentSymbolTable` containing all discovered types and a list of `ParsedCompilationUnit` objects with resolved method invocations.

### 5. Algorithm & Mechanism
- **Two-Pass Lexical Traversal:**
  - **Pass 1 ($O(N)$ files):** Uses JavaParser's `JavaParser.parse(String)` to generate an Abstract Syntax Tree (AST). `JavaParserDeclarationVisitor` visits `ClassOrInterfaceDeclaration`, `RecordDeclaration`, and `EnumDeclaration`. Deduplicates FQCNs via `StringFlyweightPool` into `ConcurrentSymbolTable`.
  - **Pass 2 ($O(N)$ files):** `Pass2InvocationResolver` uses a `LexicalScopeStack` to resolve method calls (`MethodCallExpr`) and object creation (`ObjectCreationExpr`) against imported types, same-package siblings, and superclasses.

### 6. Design Choices & Alternatives
- **Why JavaParser over `javac` / Eclipse JDT?** JavaParser is lightweight, embeddable, runs standalone without Maven/Gradle compilation artifacts, and handles incomplete or partially unresolvable enterprise codebases gracefully.
- **Why two passes?** A single pass cannot resolve forward references or circular class dependencies across compilation units without backtracking.

### 7. Limitations & Interview Challenges
- **Limitation:** Incomplete symbol resolution when third-party libraries are not present in source form. E.g., calling `StringUtils.isEmpty()` resolves to `org.apache.commons.lang3.StringUtils` only if the import is present or FQCN is typed.
- **Interviewer Challenge:** *"How do you handle method overloading if types aren't fully resolved?"*
  - **Answer:** CodeContext matches by method name, parameter arity, and best-effort syntactic type names; where ambiguous, it creates candidate call edges or marks resolution confidence as `LOW` or `MEDIUM`.

### 8. Concrete Code Example & Execution Trace
```java
package com.example;
import com.example.service.OrderService;

public class OrderController {
    private final OrderService service = new OrderService();
    public void checkout() {
        service.processOrder();
    }
}
```
**Trace:**
1. Pass 1 visits `OrderController.java` $\to$ records `TypeDefinition("com.example.OrderController")` in `ConcurrentSymbolTable`.
2. Pass 1 visits `OrderService.java` $\to$ records `TypeDefinition("com.example.service.OrderService")`.
3. Pass 2 scans `checkout()`: finds `service.processOrder()`. Checks `LexicalScopeStack` $\to$ field `service` has type `OrderService` $\to$ import maps to `com.example.service.OrderService` $\to$ creates invocation record from `com.example.OrderController` to `com.example.service.OrderService`.

---

## Module 2: Call Graphs & Architectural Graph Analytics (JGraphT)

### 1. Problem Solved
Quantifying architectural coupling, finding cyclical spaghetti architecture, identifying single points of failure (structural hotspots), and computing the ripple effect (blast radius) of modifying code.

### 2. Beginner-Friendly Explanation
If code is a city, classes are buildings and method calls are roads. Graph analytics tells you which buildings have the most traffic (PageRank), which roads form roundabouts where you get trapped forever (Circular Cycles), and which neighborhoods will lose power if a power plant goes down (Blast Radius).

### 3. Entry Point & Core Classes
- **Entry Point:** `DependencyGraphBuilder.java`
- **Core Processing:** `CycleDetectionEngine.java`, `PageRankEngine.java`, `BlastRadiusEngine.java`, `PathTraceEngine.java`, `CompositeRiskEngine.java`
- **Data Models:** `TypeVertex.java`, `DependencyEdge.java`, `CycleReport.java`, `HotspotMetric.java`

### 4. Input & Output
- **Input:** Resolved `ParsedCompilationUnit` list and `ConcurrentSymbolTable`.
- **Output:** JGraphT `DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge>`, cycle reports, PageRank scores, shortest path traces.

### 5. Algorithm & Mechanism
- **Graph Type:** Directed, weighted simple graph. Vertices = FQCN. Edges = Directed invocation from Caller to Callee. Edge weight = Number of static invocations.
- **Cycle Detection:** Employs `KosarajuStrongConnectivityInspector` ($O(V + E)$) to detect Strongly Connected Components (SCCs). For components with $>1$ vertex, executes bounded DFS cycle extraction up to length 7 and maximum 50 paths to avoid exponential combinatorial explosion.
- **PageRank:** Standard power-iteration algorithm over incoming edges:
  $$PR(u) = \frac{1 - d}{|V|} + d \sum_{v \in B_u} \frac{PR(v)}{L(v)}$$
  with damping factor $d = 0.85$, convergence threshold $10^{-6}$, max 100 iterations.
- **Blast Radius:** Inverts graph edges (`Callee -> Caller`) and executes breadth-first search (BFS) up to user-specified depth (default 3, configurable). Segregates direct callers (depth 1) from transitive-only callers (depth $\ge 2$).
- **Shortest Path Trace:** Uses BFS from source vertex to target vertex, recording the sequence of vertices and edge invocation metadata (method names and line numbers).

### 6. Design Choices & Alternatives
- **Why JGraphT?** Battle-tested, highly optimized memory layout, rich collection of graph algorithms (Kosaraju, Dijkstra, PageRank).
- **Why Kosaraju over Tarjan?** Kosaraju uses two simple DFS passes (forward and transposed graph) which is exceptionally robust in Java without deep recursion stack issues on large graphs.

### 7. Limitations & Interview Challenges
- **Limitation:** Class-level granularity. A cycle between Class A and Class B does not necessarily mean method A1 calls B1 and B1 calls A1 (could be A1 calls B1 and B2 calls A2).
- **Interviewer Challenge:** *"Why not just run Johnson's elementary cycle algorithm?"*
  - **Answer:** Johnson's algorithm is $O((V + E)(C + 1))$. In heavily coupled enterprise codebases, $C$ can be in the millions, causing out-of-memory or freezing the CLI. CodeContext isolates SCCs first, and caps extraction to bounded cycle paths for immediate actionable refactoring.

---

## 3. Module 3: Cryptographic Security & Expression Resolution

### 1. Problem Solved
Detecting insecure cryptographic algorithms (DES, ECB mode, MD5, SHA-1) in Java code, identifying configuration issues, and generating automated remediation.

### 2. Beginner-Friendly Explanation
Developers frequently use cryptography libraries like `Cipher.getInstance("DES")` or `Cipher.getInstance("AES")` without realizing that DES is cracked in seconds, or that bare `"AES"` defaults to insecure ECB mode depending on the JVM provider. CodeContext acts as an automated cryptographic reviewer that inspects every crypto call in the code.

### 3. Entry Point & Core Classes
- **Entry Point:** `SecurityAnalysisEngine.java`
- **Core Processing:** `CryptoApiDetector.java`, `LimitedExpressionResolver.java`, `SecurityRuleRegistry.java`
- **Rules:** `EcbModeSecurityRule.java`, `LegacyCipherSecurityRule.java`, `WeakDigestSecurityRule.java`, `ProviderDependentCipherSecurityRule.java`, `ObsoleteTlsSecurityRule.java`
- **Data Models:** `CryptoApiUsage.java`, `Finding.java`, `RuleMetadata.java`

### 4. Input & Output
- **Input:** Target repository source code.
- **Output:** `SecurityAnalysisResult` containing a list of `Finding` objects and `CryptoApiUsage` observations.

### 5. Algorithm & Mechanism
- **Pattern Matching:** `CryptoApiDetector` hooks into JavaParser AST visitors to detect static factory calls (`getInstance`) across 9 JCA classes (`Cipher`, `MessageDigest`, `Signature`, `KeyGenerator`, `KeyPairGenerator`, `KeyAgreement`, `Mac`, `SecureRandom`, `SSLContext`).
- **Constant Resolution (`LimitedExpressionResolver`):**
  - Resolves string literals (`"DES/CBC/PKCS5Padding"`).
  - Resolves intra-class `static final String` fields.
  - Resolves binary string concatenations (`ALGO + "/ECB/PKCS5Padding"`).
  - Flags dynamic parameters (`request.getParameter(...)`, `props.getProperty(...)`) as `ResolutionStatus.UNKNOWN`.
- **Rule Verification:**
  - `CC-CRYPTO-001` (ECB Mode): Evaluates if transformation contains `/ECB/`. **Crucial:** Inspects algorithm prefix; if algorithm starts with `RSA`, it is public-key encryption and is NOT flagged as insecure ECB.
  - `CC-CRYPTO-002` (Legacy Cipher): Matches against `DES`, `DESede`, `RC2`, `RC4`, `Blowfish`.
  - `CC-CRYPTO-003` (Weak Digest): Matches against `MD5`, `SHA-1`, `SHA1`.

---

## 4. Module 4: CycloneDX 1.6 Cryptography Bill of Materials (CBOM)

### 1. Problem Solved
Organizations need an auditable, machine-readable inventory of all cryptographic assets (algorithms, keys, certificates, protocols) across their software supply chain, mandated by modern compliance standards (NIST SP 800-219, US Executive Order 14028).

### 2. Beginner-Friendly Explanation
Just like a food label lists ingredients so you know if there are allergens, a CBOM is a standardized JSON document that lists every encryption algorithm and hash function in your application so security teams know if you are using weak or quantum-vulnerable cryptography.

### 3. Entry Point & Core Classes
- **Entry Point:** `CryptoCbomCommand.java`
- **Core Processing:** `CryptoInventoryEngine.java`, `CycloneDxCbomExporter.java`
- **Data Models:** `CryptoInventory.java`, `CryptoAsset.java`, `CryptoPrimitiveType.java`

### 4. Input & Output
- **Input:** List of `CryptoApiUsage` instances from static scanner.
- **Output:** Valid CycloneDX 1.6 JSON document (`crypto-bom.json`).

### 5. Schema & Standards Compliance
- Conforms to **CycloneDX 1.6** specification.
- Validated using official schema `bom-1.6.schema.json` with 0 errors.
- Correctly maps cryptographic primitive types:
  - RSA Cipher $\to$ `pke` (Public Key Encryption)
  - AES / DES Cipher $\to$ `block-cipher`
  - MessageDigest $\to$ `hash`
  - KeyGenerator $\to$ `kdf`
  - SecureRandom $\to$ `drbg`
- Includes occurrence evidence (file path, line number, offset).
- **Zero Secret Exposure:** Never includes private keys, password strings, or secret material in the CBOM.

---

## 5. Module 5: Post-Quantum Cryptography (PQC) & Blast Radius

### 1. Problem Solved
Preparing enterprise Java architectures for the quantum computing era when Shor's algorithm will break classical asymmetric encryption (RSA, ECC, Diffie-Hellman), guiding migration to NIST FIPS 203/204/205 standards.

### 2. Beginner-Friendly Explanation
Quantum computers will easily break RSA encryption by solving prime factorization in minutes. Security teams need to know: *Where in our million lines of code are we using RSA? What is it used for? And who calls it?* CodeContext answers this by finding all classical public-key usages and using the call graph to compute every upstream class that depends on them.

### 3. Entry Point & Core Classes
- **Entry Point:** `CryptoPqcCommand.java`
- **Core Processing:** `PqcMigrationEngine.java`, `PqcAssetAssessment.java`, `CryptoAssetBlastRadius.java`
- **Data Models:** `AssessmentCategory.java`, `CryptoPurpose.java`, `QuantumExposureLevel.java`

### 4. Categorization & Guidance
- **PQC Migration Candidate:** Shor's algorithm quantum-vulnerable (RSA, ECDH, ECDSA).
  - **RSA Encryption:** Replace with ML-KEM (NIST FIPS 203) using hybrid KEM + AEAD envelope. ML-KEM is *not* a drop-in `Cipher` replacement.
  - **RSA Signatures:** Replace with ML-DSA (NIST FIPS 204) or SLH-DSA (NIST FIPS 205).
- **Classical Remediation:** Algorithms broken *today* on classical computers (DES, MD5, SHA-1). Remediate to AES-256-GCM / SHA-384 before worrying about quantum threats.
- **Quantum Consideration:** Modern symmetric ciphers (AES-256) and secure hashes (SHA-384, SHA-512). Grover's algorithm provides quadratic speedup ($O(\sqrt{N})$), requiring 256-bit keys for 128-bit quantum security margin.
- **Unknown / Unresolved:** Dynamic or provider-uncertain usages (`SHA1PRNG`).

### 5. Graph-Based PQC Blast Radius
- Maps cryptographic usage to enclosing compilation unit FQCN.
- Inverts call graph edges to traverse callers.
- Reports direct callers (depth 1) and transitive-only callers (depth $\ge 2$).
- Detects high fan-in shared utilities (`Utils.java`) where 400+ callers converge.
- Explicitly discloses: *Class-level dependency reachability does not prove runtime method invocation*.
