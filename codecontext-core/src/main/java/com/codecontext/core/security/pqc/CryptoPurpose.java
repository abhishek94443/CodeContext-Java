package com.codecontext.core.security.pqc;

/**
 * Precise cryptographic purpose derived from the immediate API operation and primitive type.
 * Distinguishes immediate API function from inferred downstream application use.
 * Preserves uncertainty where downstream application intent cannot be statically proven.
 */
public enum CryptoPurpose {
    /**
     * Asymmetric key pair generation (e.g. KeyPairGenerator.getInstance("RSA")).
     * Downstream usage (signing vs encryption/key exchange) is preserved as undetermined
     * unless explicitly evidenced.
     */
    KEY_PAIR_GENERATION("Key Pair Generation"),

    /**
     * Symmetric secret key generation (e.g. KeyGenerator.getInstance("AES"), KeyGenerator.getInstance("DES")).
     */
    SYMMETRIC_KEY_GENERATION("Symmetric Key Generation"),

    /**
     * Cryptographically secure pseudo-random number generation (e.g. SecureRandom.getInstance("SHA1PRNG")).
     */
    SECURE_RANDOM("Secure Random Generation"),

    /**
     * Digital signature generation and verification (e.g. Signature.getInstance("SHA256withRSA")).
     */
    DIGITAL_SIGNATURE("Digital Signature"),

    /**
     * Asymmetric key agreement / exchange protocol (e.g. KeyAgreement.getInstance("ECDH"), KeyAgreement.getInstance("DH")).
     */
    KEY_AGREEMENT("Key Agreement"),

    /**
     * Public-key data encryption or key transport (e.g. Cipher.getInstance("RSA/ECB/OAEPWithSHA-512AndMGF1Padding")).
     */
    PUBLIC_KEY_ENCRYPTION("Public Key Encryption"),

    /**
     * Symmetric bulk data encryption / decryption (e.g. Cipher.getInstance("AES/GCM/NoPadding")).
     */
    SYMMETRIC_ENCRYPTION("Symmetric Bulk Encryption"),

    /**
     * Unkeyed cryptographic message digest / hash function (e.g. MessageDigest.getInstance("SHA-256")).
     */
    HASH_FUNCTION("Cryptographic Hash Function"),

    /**
     * Keyed message authentication code (e.g. Mac.getInstance("HmacSHA256")).
     */
    MAC("Message Authentication Code"),

    /**
     * Secure transport protocol (e.g. SSLContext.getInstance("TLSv1.3")).
     */
    SECURE_TRANSPORT("Secure Transport (TLS)"),

    /**
     * Cryptographic purpose cannot be reliably determined from static context.
     */
    UNKNOWN("Unknown Purpose");

    private final String description;

    CryptoPurpose(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
