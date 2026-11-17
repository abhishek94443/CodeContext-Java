
> Correctness clarification: historical counts and claims below describe earlier runs or design intentions. The production scanner does not use StringFlyweightPool. Historical Gson values 722 and 1,333 are total graph vertices (including boundary vertices) and aggregated directed edges, not source classes or call occurrences. The 12 reported paths were truncated cycle examples, not complete enumeration or an SCC count. Graph paths are class-level reachability, not guaranteed runtime execution. Only SCC computation is linear; cycle enumeration and BFS have the documented limits, not fixed resource guarantees. CBOM schema tests cover particular fixtures and do not prove all semantic mappings. See [current correctness report](../CORRECTNESS_REMEDIATION.md).

# CodeContext Java — Master Audit Manifest & Checksums

**Audit Date:** 2026-11-14T20:40:00+05:30  
**Auditor Role:** Principal Java Engineer & Independent Technical Auditor  

---

## 1. System & Target Repositories Manifest

| Component | Target Path | Git Commit SHA / Version | Notes |
|---|---|---|---|
| **CodeContext Source Root** | `c:\Users\Lenovo\Desktop\abhishe\shivansh-codecontext\codecontext-java` | `9cc5193a9927ec91f1e8bf41544868cbd78fd081` | Merged `main` branch (Ahead of origin by 4 commits) |
| **Google Gson (Architecture Test)** | `C:\Users\Lenovo\Desktop\codecontext\CodeContext\temp_repos\gson` | `9835b6f90192f79cdf5300c528fc6212455fb6c1` | 264 Java files, ~50,000 LOC, branch `main` |
| **OWASP BenchmarkJava (Security Test)**| `C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava` | `a452b3aafd232245d0753e54eb243d6651f8ef96` | 2,767 Java files, ~245,500 LOC, branch `master` |
| **Benchmark Oracle File** | `C:\Users\Lenovo\Desktop\codecontext\seccheck\BenchmarkJava\expectedresults-1.2.csv` | File Size: 98,069 bytes | 2,740 test cases (482 in CWE-327 & 328) |
| **Java Runtime Environment** | `C:\Program Files\Java\jdk-21` | Oracle OpenJDK `21.0.3+7-LTS-152` | 64-Bit Server VM |
| **Build Tooling** | `./gradlew` | Gradle `9.1.0` | Daemon JVM: JDK 21 |
| **Host System** | AMD Ryzen 5 5500U (6C/12T), 6 GB RAM | Windows 11 Build 26200 | Total visible memory ~5.85 GiB |

---

## 2. Test Suite Execution Summary
- **Total Test Cases Executed:** **282 tests**
  - `codecontext-core`: 210 tests (100% pass)
  - `codecontext-cli`: 72 tests (100% pass)
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Build Duration:** 40 seconds clean

---

## 3. Generated Audit Artifacts & SHA-256 Cryptographic Checksums

| Relative File Path | Size (Bytes) | SHA-256 Checksum |
|---|---|---|
| `audit-output/FINAL_CODECONTEXT_AUDIT.md` | 19,250 | `DD6EECDA83F4C542E78C768B8E50306D4D69D0302FDEDDF6C25A5AF3DC18F135` |
| `audit-output/MERGE_INTEGRITY.md` | 6,760 | `3C90229B0541654748439CBEB2735178F676F7AFA5DCE4D67375EB389EDBEA65` |
| `audit-output/FEATURE_MATRIX.md` | 8,658 | `0B14027A9856BF81534D34DEFF902DE24C528FC42D59E3F86A1D6B88B2A91D34` |
| `audit-output/ARCHITECTURE_WALKTHROUGH.md` | 9,952 | `AA6D401388335F57E51F7FF58C45154FA48D7CF43EB0A5E2E200D2DFF57B2F83` |
| `audit-output/SECURITY_AND_PQC_REVIEW.md` | 12,684 | `CCE58CA3BF833E52A64F268F754A0EFB1DD3863DB0CC09BEBDD35AC1C46E3ED5` |
| `audit-output/PERFORMANCE_BENCHMARK.md` | 4,777 | `98DC1011AF1B6A49A74D007B290466123FB7FE4B225090C8B7DE8287F868779A` |
| `audit-output/INTERVIEW_LEARNING_GUIDE.md` | 13,520 | `C7A578856D71AA0D0D510D131812BC867353426EECE789FE61F7A831E0594E68` |
| `audit-output/INTERVIEW_DEMO_PLAN.md` | 9,404 | `7C58FD7583930E0AF0C39D06FD44D0305B276AABECECB8A48DFEB4EFA4B24B3B` |
| `audit-output/RESUME_EVIDENCE.md` | 6,057 | `A047B44256925E7470C5FC913CE1F3EBB2629E2C82433AE55F60B8BF9E84AE79` |
| `audit-output/COMMANDS_EXECUTED.md` | 5,675 | `DBB0A85F2E8B72B2B01DFF5498F0400E44D31BC0B5CBF0895318F1212B3B60DC` |
| `audit-output/gson/architecture-summary.json` | 3,774 | `FA3570D6A2E77B07D108B69F4595175E64C4D5E0102CFC21FEBE542DFEFB5B6D` |
| `audit-output/gson/codecontext-graph.html` | 235,862 | `0CFA070F557E6C279D347573FC79C13923916529DF6C1C526A4A8E029AEF6BF7` |
| `audit-output/gson/codecontext.sarif` | 10,592 | `3AF65D228A81CBEF38103E1D1088B6A7595417A12DB877CC4EB831412A45BCF2` |
| `audit-output/gson/diff-impact-summary.md` | 15,589 | `44441C90E084883F258AB82670B65985FE1B68AA6B9D97E0C0DF294026FFFA90` |
| `audit-output/gson/gson-cycles.json` | 9,132 | `BE31E930960D5AE1916EE9B2061172EBCEDE022EC6C4092E46C186414AC8499B` |
| `audit-output/gson/gson-impact-gson.json` | 38,532 | `28AAF9CF1EBD77A8812641C51DDB6E11BFAD80E24C8C570881C8407B4A217B7A` |
| `audit-output/gson/gson-plan.json` | 12,101 | `8F67FF20158C7AE187574E51ABD5F4ABF36433DD82327CC4CE8DAE14EB094910` |
| `audit-output/benchmark/benchmark-scoring-reconciliation.csv` | 39,978 | `9781CA53FA160B1A8C832E9D238B311FE4D52BEA49C023D7D14B3E03C174415C` |
| `audit-output/benchmark/codecontext-security.html` | 1,096,462 | `BD030207FB142FCD92E176A3455CECAE6456EFF29A81B0E129CA9C6500F8C581` |
| `audit-output/benchmark/crypto-bom.json` | 177,627 | `6176F6DE28F7DDD283FD677372AD2B05C0E9C06865705904D96775EC20C888A4` |
| `audit-output/benchmark/crypto-inventory.json` | 330,796 | `5AB8DEAB54B3B1F01EAD9F87F83512551A694AE94E24DE24BCB54DF4D6B21731` |
| `audit-output/benchmark/performance-measurements.csv` | 652 | `65E81BC83F62D21C563E0FADD96D82C5C93D810C1246513A5C973EC755F602B7` |
| `audit-output/benchmark/pqc-assessment.json` | 532,983 | `981DA1CD7CEC80BCE5098D156DC109A87950A789649F4D4A7F41604B5034D4AC` |
| `audit-output/benchmark/security-summary.json` | 212,210 | `676BD824FC92E1814480F9B86C4353A4DECF43CC0DEBA0184A3B4BC51A5A5FA2` |

---

## 4. Measurement Methodology Disclosures
- **Warmup Separation:** A dedicated initial run was executed before timed measurements to ensure JVM JIT compilation and Gradle daemon cache stabilization.
- **Clock Instrumentation:** Times were measured using native Windows .NET `[System.Diagnostics.Stopwatch]` with microsecond precision.
- **Hardware Isolation:** Background indexing and heavy compilation tasks were completed prior to benchmarking runs.
