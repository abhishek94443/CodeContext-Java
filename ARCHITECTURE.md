# CodeContext-Java Architecture

CodeContext-Java is a deterministic codebase intelligence engine built for enterprise Java repositories. It combines **Abstract Syntax Tree (AST) lexical analysis**, **directed multigraph algorithms**, and **Git commit forensics** to compute mathematical ground truths about software architecture before invoking any probabilistic AI reasoning.

---

## High-Level Architecture Pipeline

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. INGESTION & PARSING (codecontext-core)                                    │
│    • JavaParser 3.25.8 with Bounded Virtual Thread Concurrency              │
│    • Pass 1: Fast declaration inventory (Classes, Records, Interfaces)     │
│    • Tri-Index ConcurrentSymbolTable with Flyweight String Interning        │
│    • Block-level LexicalScopeStack for local variable type inference        │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. TWO-PASS INVOCATION RESOLUTION                                            │
│    • Pass 1.5: Type Hierarchy DAG tracking `extends` and `implements`       │
│    • Pass 2: 6-Step Chain-of-Responsibility Resolver                         │
│      1. Same-File Lookup ──► 2. Same-Package Resolution                      │
│      3. Explicit Imports ──► 4. Wildcard Imports (zero false edges)         │
│      5. Inherited Methods ──► 6. External / Boundary Fallback                │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. GRAPH INTELLIGENCE & TOPOLOGY (JGraphT)                                  │
│    • Directed Weighted Multigraph G = (V, E)                                │
│    • Boundary Node Weighting (JDK, Spring, Jackson filtered)                │
│    • PageRank Centrality: Architectural gravity hubs & afferent coupling    │
│    • Tarjan SCC & Johnson Algorithm: Exact circular dependency paths        │
│    • Transposed Graph BFS: Upstream change blast radius & caller trees      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. GIT EVOLUTION FORENSICS & RISK SCORING                                   │
│    • Pure Java Git integration via Eclipse JGit                             │
│    • 90-day churn analysis: additions, deletions, commit velocity           │
│    • Author entropy: multi-developer modification hotspots                  │
│    • CompositeRiskScore: dynamic weighting (PageRank + Cycles + Churn)      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                     ▼
┌──────────────────────────────────────┐┌──────────────────────────────────────┐
│ 5. STANDALONE HTML VISUALIZER        ││ 6. DEVELOPER CLI ENGINE              │
│    • Zero-dependency single-file HTML││    • Picocli sub-100ms startup       │
│    • Interactive canvas with D3 /    ││    • `analyze`: Full repo telemetry  │
│      Cytoscape-inspired layout       ││    • `cycles`: CI gate (--fail-on-   │
│    • Package grouping & focus lenses ││      cycles) with sysexits codes     │
└──────────────────────────────────────┘└──────────────────────────────────────┘
```

---

## Core Modules

### 1. `codecontext-core`
- **Zero Framework Core:** Pure Java 21, zero Spring or Web dependencies.
- **Memory Bounded:** Uses semaphores to cap active AST memory during large-scale scans.
- **Platform Agnostic:** Standardized POSIX path normalization across Windows, macOS, and Linux.

### 2. `codecontext-cli`
- Command-line runner built on Picocli 4.7.6.
- Sysexits-aligned process exit codes (0 = Clean, 1 = Policy Violation, 2 = Bad Input, 3 = Fatal).
- Auto-detecting ANSI color output with clean ASCII fallback.

### 3. `codecontext-service` (Scaffold)
- Spring Boot 3 foundation reserved for upcoming AI agent tool execution and REST orchestration.
