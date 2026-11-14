package com.codecontext.core.security.pqc;

/**
 * Post-Quantum Cryptography exposure classification.
 * Strictly separates classical public-key quantum exposure (Shor's algorithm),
 * symmetric/hash quantum considerations (Grover/BHT), classical algorithm weakness,
 * and recognized standardized PQC algorithms.
 */
public enum QuantumExposureLevel {
    /**
     * Classical asymmetric public-key cryptography completely broken by Shor's algorithm
     * on a Cryptanalytically Relevant Quantum Computer (CRQC).
     * Discrete logarithm, ECDLP, and integer factorization are solvable in polynomial time O(n^3).
     */
    QUANTUM_VULNERABLE("Quantum Vulnerable (Shor)"),

    /**
     * Classically secure symmetric ciphers and modern hash functions subject to
     * quadratic quantum speedup (Grover for key/preimage search O(2^(k/2)); BHT for collisions O(2^(n/3))).
     * AES-256 retains 128-bit post-quantum security; AES-128 drops to 64-bit;
     * SHA-256/384/512 remain robust against practical quantum collision attacks due to extreme QRAM requirements.
     */
    QUANTUM_IMPACTED("Quantum Impacted (Grover/BHT)"),

    /**
     * Legacy algorithms that are already broken or deprecated under classical cryptanalysis
     * (e.g. DES 56-bit key exhaustion; MD5/SHA-1 collision attacks; RC4 keystream bias).
     * Insecurity is fundamentally classical, not primarily a quantum threat.
     */
    CLASSICALLY_WEAK("Classically Weak / Broken"),

    /**
     * Approved post-quantum algorithms compliant with final NIST standards
     * (FIPS 203 ML-KEM, FIPS 204 ML-DSA, FIPS 205 SLH-DSA, LMS, XMSS).
     */
    QUANTUM_SAFE("Quantum Safe (NIST PQC)"),

    /**
     * Dynamic, unresolved, or unrecognized cryptographic argument.
     */
    UNKNOWN("Unknown Exposure");

    private final String description;

    QuantumExposureLevel(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
