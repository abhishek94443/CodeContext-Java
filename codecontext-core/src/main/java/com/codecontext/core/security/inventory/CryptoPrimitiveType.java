package com.codecontext.core.security.inventory;

/**
 * Standardized cryptographic primitive types matching NIST and CycloneDX CBOM classifications.
 */
public enum CryptoPrimitiveType {
    BLOCK_CIPHER("block-cipher"),
    STREAM_CIPHER("stream-cipher"),
    PUBLIC_KEY_ENCRYPTION("pke"),
    MESSAGE_DIGEST("hash"),
    MAC("mac"),
    SIGNATURE("signature"),
    KEY_AGREEMENT("key-agree"),
    KEY_GENERATOR("kdf"),
    KEY_PAIR_GENERATOR("pke"),
    SECURE_RANDOM("drbg"),
    TLS_PROTOCOL("other"),
    OTHER("other");

    private final String cycloneDxName;

    CryptoPrimitiveType(String cycloneDxName) {
        this.cycloneDxName = cycloneDxName;
    }

    public String cycloneDxName() {
        return cycloneDxName;
    }
}
