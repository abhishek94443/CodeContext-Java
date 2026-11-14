package com.codecontext.core.security.crypto;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Representation of a cryptographic algorithm or JCA transformation string.
 * Supports standard JCA transformations formatted as "algorithm/mode/padding" or "algorithm".
 */
public record CryptoAlgorithm(
        String rawName,
        String family,
        Optional<String> mode,
        Optional<String> padding
) {
    public CryptoAlgorithm {
        Objects.requireNonNull(rawName, "rawName cannot be null");
        family = family == null ? rawName : family;
        mode = mode == null ? Optional.empty() : mode;
        padding = padding == null ? Optional.empty() : padding;
    }

    /**
     * Parses a raw JCA transformation string (e.g. "AES/ECB/PKCS5Padding" or "MD5").
     */
    public static CryptoAlgorithm parse(String rawTransformation) {
        if (rawTransformation == null || rawTransformation.isBlank()) {
            return new CryptoAlgorithm("", "", Optional.empty(), Optional.empty());
        }

        String trimmed = rawTransformation.trim();
        String[] parts = trimmed.split("/");

        String family = parts[0].trim();
        Optional<String> mode = parts.length > 1 && !parts[1].isBlank()
                ? Optional.of(parts[1].trim())
                : Optional.empty();
        Optional<String> padding = parts.length > 2 && !parts[2].isBlank()
                ? Optional.of(parts[2].trim())
                : Optional.empty();

        return new CryptoAlgorithm(trimmed, family, mode, padding);
    }

    /**
     * Checks if this cipher transformation omits explicit mode and padding specifications (e.g. "AES").
     * When mode and padding are omitted, the runtime JCE provider chooses the defaults, which is
     * provider-dependent and often insecure (e.g. SunJCE defaults to ECB mode).
     */
    public boolean isBareAlgorithmWithoutModeOrPadding() {
        return mode.isEmpty() && padding.isEmpty() && !rawName.contains("/") && !rawName.isBlank();
    }

    /**
     * Checks if this algorithm belongs to an asymmetric (public-key) cipher family (e.g. RSA, ElGamal, ECIES).
     */
    public boolean isAsymmetricCipher() {
        String f = family.toUpperCase(Locale.ROOT);
        return f.contains("RSA") || f.contains("ELGAMAL") || f.contains("ECIES");
    }

    /**
     * Checks if this algorithm/transformation explicitly specifies Electronic Codebook (ECB) mode.
     * Only applicable to symmetric ciphers. Asymmetric algorithms (e.g. RSA) are not symmetric block ciphers
     * even if historical JCA transformation syntax permits "RSA/ECB/...".
     */
    public boolean isEcbMode() {
        if (isAsymmetricCipher()) {
            return false;
        }
        if (mode.isPresent() && mode.get().equalsIgnoreCase("ECB")) {
            return true;
        }
        // Direct match or partial check on raw string
        String upper = rawName.toUpperCase(Locale.ROOT);
        return upper.matches(".*[/]ECB([/].*)?") || upper.equals("ECB");
    }

    /**
     * Checks if this algorithm is a broken legacy symmetric cipher (e.g. DES, RC2, RC4/ARCFOUR, Blowfish).
     */
    public boolean isLegacyInsecureCipher() {
        String f = family.toUpperCase(Locale.ROOT);
        return f.equals("DES") || f.equals("RC2") || f.equals("RC4") || f.equals("ARCFOUR") || f.equals("BLOWFISH");
    }

    /**
     * Checks if this algorithm is Triple DES (DESede).
     */
    public boolean isTripleDes() {
        String f = family.toUpperCase(Locale.ROOT);
        return f.equals("DESEDE") || f.equals("TRIPLEDES") || f.equals("3DES") || f.startsWith("DES/EDE");
    }

    /**
     * Checks if this algorithm is a weak or collision-broken hash function (e.g. MD2, MD4, MD5, SHA-1).
     */
    public boolean isWeakDigest() {
        String f = family.toUpperCase(Locale.ROOT).replace("-", "");
        return f.equals("MD2") || f.equals("MD4") || f.equals("MD5") || f.equals("SHA1");
    }

    /**
     * Checks if this algorithm/protocol is an obsolete TLS/SSL protocol.
     */
    public boolean isObsoleteTlsProtocol() {
        String f = family.toUpperCase(Locale.ROOT);
        return f.equals("SSL") || f.equals("SSLV2") || f.equals("SSLV3")
                || f.equals("TLSV1") || f.equals("TLSV1.0") || f.equals("TLSV1.1");
    }
}

