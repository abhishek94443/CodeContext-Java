package com.codecontext.core.security.inventory;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.crypto.CryptoAlgorithm;
import com.codecontext.core.security.crypto.CryptoApiOwner;
import com.codecontext.core.security.crypto.CryptoApiUsage;
import com.codecontext.core.security.crypto.ResolutionStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Normalization and aggregation engine that transforms raw CryptoApiUsage observations
 * into a canonical, deduplicated Cryptographic Inventory.
 */
public class CryptoInventoryEngine {

    /**
     * Aggregates and normalizes raw cryptographic observations into a canonical CryptoInventory.
     *
     * @param usages   Raw cryptographic API usages discovered during analysis.
     * @param repoRoot Optional repository root used to compute stable relative paths.
     * @return Deterministically ordered CryptoInventory.
     */
    public CryptoInventory buildInventory(Collection<CryptoApiUsage> usages, Path repoRoot) {
        if (usages == null || usages.isEmpty()) {
            return CryptoInventory.empty();
        }

        // Group usages by canonical asset identity key
        Map<String, List<CryptoAssetUsage>> groupedUsages = new LinkedHashMap<>();
        Map<String, CanonicalAssetDescriptor> descriptors = new LinkedHashMap<>();

        for (CryptoApiUsage u : usages) {
            Path relPath = normalizeRelativePath(u.filePath(), repoRoot);
            String usageId = relPath.toString().replace('\\', '/') + ":" +
                    u.location().startLine() + ":" + u.location().startColumn();

            // Extract enclosing class/method info if captured in properties or location
            Optional<String> enclosingClass = Optional.ofNullable(u.properties().get("enclosingClass"));
            Optional<String> enclosingMethod = Optional.ofNullable(u.properties().get("enclosingMethod"));

            CryptoAssetUsage assetUsage = new CryptoAssetUsage(
                    usageId,
                    relPath,
                    u.location(),
                    enclosingClass,
                    enclosingMethod,
                    u.owner(),
                    u.methodName(),
                    u.originalArgumentExpr(),
                    u.argumentResolution(),
                    u.ownerConfidence()
            );

            CanonicalAssetDescriptor desc = resolveDescriptor(u);
            String groupKey = desc.groupKey();

            List<CryptoAssetUsage> assetList = groupedUsages.computeIfAbsent(groupKey, k -> new ArrayList<>());
            boolean alreadyPresent = assetList.stream().anyMatch(existing -> existing.usageId().equals(usageId));
            if (!alreadyPresent) {
                assetList.add(assetUsage);
            }
            descriptors.putIfAbsent(groupKey, desc);
        }

        List<CryptoAsset> assets = new ArrayList<>();
        List<CryptoAssetRelationship> relationships = new ArrayList<>();

        for (Map.Entry<String, List<CryptoAssetUsage>> entry : groupedUsages.entrySet()) {
            String groupKey = entry.getKey();
            List<CryptoAssetUsage> assetUsages = entry.getValue();
            CanonicalAssetDescriptor desc = descriptors.get(groupKey);

            // Determine aggregate confidence
            boolean anyHigh = assetUsages.stream().anyMatch(us -> us.confidence() == AnalysisConfidence.HIGH);
            AnalysisConfidence aggConfidence = anyHigh ? AnalysisConfidence.HIGH : AnalysisConfidence.LOW;

            String stableAssetId = generateStableAssetId(desc);

            CryptoAsset asset = new CryptoAsset(
                    stableAssetId,
                    desc.canonicalName(),
                    desc.algorithmFamily(),
                    desc.primitiveType(),
                    desc.mode(),
                    desc.padding(),
                    assetUsages,
                    desc.isResolved(),
                    aggConfidence
            );
            assets.add(asset);

            for (CryptoAssetUsage u : assetUsages) {
                relationships.add(new CryptoAssetRelationship(
                        u.usageId(),
                        stableAssetId,
                        "USES_ALGORITHM"
                ));
            }
        }

        // Deterministic sort
        Collections.sort(assets);

        // Compute summary metrics
        int totalObservations = usages.size();
        int uniqueAssets = assets.size();
        int resolvedAssets = (int) assets.stream().filter(CryptoAsset::isResolved).count();
        int unresolvedObservations = (int) assets.stream()
                .filter(a -> !a.isResolved())
                .mapToInt(CryptoAsset::usageCount)
                .sum();

        Set<String> filesWithCrypto = new TreeSet<>();
        Map<String, Integer> familyCounts = new LinkedHashMap<>();
        Map<CryptoPrimitiveType, Integer> primitiveCounts = new LinkedHashMap<>();

        for (CryptoAsset a : assets) {
            familyCounts.merge(a.algorithmFamily(), a.usageCount(), Integer::sum);
            primitiveCounts.merge(a.primitiveType(), a.usageCount(), Integer::sum);
            for (CryptoAssetUsage u : a.usages()) {
                filesWithCrypto.add(u.filePath().toString().replace('\\', '/'));
            }
        }

        CryptoInventorySummary summary = new CryptoInventorySummary(
                totalObservations,
                uniqueAssets,
                resolvedAssets,
                unresolvedObservations,
                filesWithCrypto.size(),
                familyCounts,
                primitiveCounts
        );

        return new CryptoInventory(assets, relationships, summary);
    }

    private CanonicalAssetDescriptor resolveDescriptor(CryptoApiUsage usage) {
        if (!usage.isOwnerConfirmed() || !usage.isAlgorithmResolved() || usage.algorithm().isEmpty()) {
            String name = "UNKNOWN-" + usage.owner().name();
            String family = "UNKNOWN";
            CryptoPrimitiveType primitive = mapOwnerToPrimitive(usage.owner(), "");
            return new CanonicalAssetDescriptor(
                    usage.owner().name() + "|UNKNOWN",
                    name,
                    family,
                    primitive,
                    Optional.empty(),
                    Optional.empty(),
                    false
            );
        }

        CryptoAlgorithm algo = usage.algorithm().get();
        String rawName = algo.rawName().trim();
        CryptoPrimitiveType primitive = mapOwnerToPrimitive(usage.owner(), algo.family());
        String family = normalizeFamily(algo.family(), usage.owner());

        // Canonical string representation
        String canonicalName;
        Optional<String> mode = algo.mode();
        Optional<String> padding = algo.padding();

        if (mode.isPresent() && padding.isPresent()) {
            canonicalName = algo.family().toUpperCase(Locale.ROOT) + "/" +
                    mode.get().toUpperCase(Locale.ROOT) + "/" +
                    padding.get();
        } else if (mode.isPresent()) {
            canonicalName = algo.family().toUpperCase(Locale.ROOT) + "/" +
                    mode.get().toUpperCase(Locale.ROOT);
        } else {
            canonicalName = rawName;
        }

        String groupKey = usage.owner().name() + "|" + canonicalName.toUpperCase(Locale.ROOT);

        return new CanonicalAssetDescriptor(
                groupKey,
                canonicalName,
                family,
                primitive,
                mode.map(m -> m.toUpperCase(Locale.ROOT)),
                padding,
                true
        );
    }

    private String normalizeFamily(String rawFamily, CryptoApiOwner owner) {
        if (rawFamily == null || rawFamily.isBlank()) {
            return "UNKNOWN";
        }
        String upper = rawFamily.toUpperCase(Locale.ROOT).replace("-", "");

        if (upper.equals("AES")) return "AES";
        if (upper.equals("DES")) return "DES";
        if (upper.equals("DESEDE") || upper.equals("3DES") || upper.equals("TRIPLEDES")) return "3DES";
        if (upper.equals("RC2")) return "RC2";
        if (upper.equals("RC4") || upper.equals("ARCFOUR")) return "RC4";
        if (upper.equals("BLOWFISH")) return "Blowfish";
        if (upper.equals("CHACHA20") || upper.startsWith("CHACHA20")) return "ChaCha20";

        if (upper.equals("MD2")) return "MD2";
        if (upper.equals("MD4")) return "MD4";
        if (upper.equals("MD5")) return "MD5";
        if (upper.equals("SHA1")) return "SHA-1";
        if (upper.startsWith("SHA2") || upper.equals("SHA256") || upper.equals("SHA384") || upper.equals("SHA512") || upper.equals("SHA224")) {
            return "SHA-2";
        }
        if (upper.startsWith("SHA3")) return "SHA-3";

        if (upper.contains("RSA")) return "RSA";
        if (upper.contains("ECDSA") || upper.contains("EC")) return "ECDSA";
        if (upper.contains("DSA")) return "DSA";
        if (upper.contains("ED25519")) return "Ed25519";
        if (upper.startsWith("HMAC")) return "HMAC";
        if (upper.equals("ECDH") || upper.equals("DH")) return "Diffie-Hellman";

        if (upper.startsWith("TLS") || upper.startsWith("SSL")) return "TLS";

        return rawFamily;
    }

    private CryptoPrimitiveType mapOwnerToPrimitive(CryptoApiOwner owner, String family) {
        String famUpper = family.toUpperCase(Locale.ROOT);
        return switch (owner) {
            case CIPHER -> {
                if (famUpper.contains("RC4") || famUpper.contains("ARCFOUR") || famUpper.contains("CHACHA20")) {
                    yield CryptoPrimitiveType.STREAM_CIPHER;
                }
                yield CryptoPrimitiveType.BLOCK_CIPHER;
            }
            case MESSAGE_DIGEST -> CryptoPrimitiveType.MESSAGE_DIGEST;
            case MAC -> CryptoPrimitiveType.MAC;
            case SIGNATURE -> CryptoPrimitiveType.SIGNATURE;
            case KEY_AGREEMENT -> CryptoPrimitiveType.KEY_AGREEMENT;
            case KEY_GENERATOR -> CryptoPrimitiveType.KEY_GENERATOR;
            case KEY_PAIR_GENERATOR -> CryptoPrimitiveType.KEY_PAIR_GENERATOR;
            case SECURE_RANDOM -> CryptoPrimitiveType.SECURE_RANDOM;
            case SSL_CONTEXT -> CryptoPrimitiveType.TLS_PROTOCOL;
        };
    }

    private String generateStableAssetId(CanonicalAssetDescriptor desc) {
        String slug = desc.canonicalName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return "crypto-asset-" + slug;
    }

    private Path normalizeRelativePath(Path path, Path repoRoot) {
        if (path == null) return Path.of("unknown");
        if (repoRoot == null) return path.getFileName() != null ? path.getFileName() : path;
        try {
            Path normRepo = repoRoot.toAbsolutePath().normalize();
            Path normPath = path.toAbsolutePath().normalize();
            if (normPath.startsWith(normRepo)) {
                return normRepo.relativize(normPath);
            }
        } catch (Exception ignored) {
        }
        return path.getFileName() != null ? path.getFileName() : path;
    }

    private record CanonicalAssetDescriptor(
            String groupKey,
            String canonicalName,
            String algorithmFamily,
            CryptoPrimitiveType primitiveType,
            Optional<String> mode,
            Optional<String> padding,
            boolean isResolved
    ) {}
}

