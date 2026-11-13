package com.codecontext.core.security.crypto;

import java.util.Objects;
import java.util.Optional;

/**
 * Enumeration of supported JCA/JCE/JSSE API owners for cryptographic operations.
 */
public enum CryptoApiOwner {
    CIPHER("javax.crypto.Cipher", "Cipher", "javax.crypto"),
    MESSAGE_DIGEST("java.security.MessageDigest", "MessageDigest", "java.security"),
    SIGNATURE("java.security.Signature", "Signature", "java.security"),
    MAC("javax.crypto.Mac", "Mac", "javax.crypto"),
    KEY_AGREEMENT("javax.crypto.KeyAgreement", "KeyAgreement", "javax.crypto"),
    KEY_GENERATOR("javax.crypto.KeyGenerator", "KeyGenerator", "javax.crypto"),
    KEY_PAIR_GENERATOR("java.security.KeyPairGenerator", "KeyPairGenerator", "java.security"),
    SECURE_RANDOM("java.security.SecureRandom", "SecureRandom", "java.security"),
    SSL_CONTEXT("javax.net.ssl.SSLContext", "SSLContext", "javax.net.ssl");

    private final String fqcn;
    private final String simpleName;
    private final String packageName;

    CryptoApiOwner(String fqcn, String simpleName, String packageName) {
        this.fqcn = fqcn;
        this.simpleName = simpleName;
        this.packageName = packageName;
    }

    public String fqcn() {
        return fqcn;
    }

    public String simpleName() {
        return simpleName;
    }

    public String packageName() {
        return packageName;
    }

    public static Optional<CryptoApiOwner> findBySimpleName(String simpleName) {
        if (simpleName == null) {
            return Optional.empty();
        }
        for (CryptoApiOwner owner : values()) {
            if (owner.simpleName.equals(simpleName)) {
                return Optional.of(owner);
            }
        }
        return Optional.empty();
    }

    public static Optional<CryptoApiOwner> findByFqcn(String fqcn) {
        if (fqcn == null) {
            return Optional.empty();
        }
        for (CryptoApiOwner owner : values()) {
            if (owner.fqcn.equals(fqcn)) {
                return Optional.of(owner);
            }
        }
        return Optional.empty();
    }
}

