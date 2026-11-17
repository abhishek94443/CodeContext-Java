package com.codecontext.core.security.inventory;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.crypto.CryptoAlgorithm;
import com.codecontext.core.security.crypto.CryptoApiOwner;
import com.codecontext.core.security.crypto.CryptoApiUsage;
import com.codecontext.core.security.crypto.ExpressionResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoInventoryEngineTest {

    private final CryptoInventoryEngine engine = new CryptoInventoryEngine();

    @Test
    @DisplayName("Groups identical configurations into single normalized asset retaining distinct call sites")
    void testIdenticalAssetGrouping() {
        Path repoRoot = Path.of("/app");
        Path file1 = Path.of("/app/src/Service1.java");
        Path file2 = Path.of("/app/src/Service2.java");

        CryptoApiUsage usage1 = createUsage(file1, 10, 5, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding");
        CryptoApiUsage usage2 = createUsage(file2, 25, 8, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding");

        CryptoInventory inventory = engine.buildInventory(List.of(usage1, usage2), repoRoot);

        assertThat(inventory.assets()).hasSize(1);
        CryptoAsset asset = inventory.assets().get(0);

        assertThat(asset.name()).isEqualTo("AES/GCM/NoPadding");
        assertThat(asset.algorithmFamily()).isEqualTo("AES");
        assertThat(asset.primitiveType()).isEqualTo(CryptoPrimitiveType.BLOCK_CIPHER);
        assertThat(asset.mode()).contains("GCM");
        assertThat(asset.padding()).contains("NoPadding");
        assertThat(asset.usageCount()).isEqualTo(2);

        assertThat(asset.usages().get(0).filePath().toString().replace('\\', '/')).isEqualTo("src/Service1.java");
        assertThat(asset.usages().get(1).filePath().toString().replace('\\', '/')).isEqualTo("src/Service2.java");

        assertThat(inventory.summary().totalObservations()).isEqualTo(2);
        assertThat(inventory.summary().uniqueAssets()).isEqualTo(1);
        assertThat(inventory.summary().resolvedAssets()).isEqualTo(1);
        assertThat(inventory.summary().filesWithCrypto()).isEqualTo(2);
    }

    @Test
    @DisplayName("Separates distinct configurations (AES/GCM vs AES/ECB) into distinct assets")
    void testDistinctConfigurationSeparation() {
        Path file = Path.of("src/App.java");
        CryptoApiUsage gcmUsage = createUsage(file, 10, 5, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding");
        CryptoApiUsage ecbUsage = createUsage(file, 20, 5, CryptoApiOwner.CIPHER, "AES/ECB/PKCS5Padding");

        CryptoInventory inventory = engine.buildInventory(List.of(gcmUsage, ecbUsage), Path.of("."));

        assertThat(inventory.assets()).hasSize(2);
        assertThat(inventory.assets().get(0).name()).isEqualTo("AES/ECB/PKCS5Padding");
        assertThat(inventory.assets().get(1).name()).isEqualTo("AES/GCM/NoPadding");

        assertThat(inventory.summary().uniqueAssets()).isEqualTo(2);
        assertThat(inventory.summary().familyCounts().get("AES")).isEqualTo(2);
    }

    @Test
    @DisplayName("Preserves UNKNOWN algorithm values as separate unresolved assets without fabricating data")
    void testUnknownValuePreservation() {
        Path file = Path.of("src/App.java");

        ExpressionResolution unknownRes = ExpressionResolution.unknown("config.getMode()", "Dynamic method call");
        CryptoApiUsage unknownUsage = new CryptoApiUsage(
                CryptoApiOwner.CIPHER,
                "getInstance",
                file,
                new SourceRange(15, 8, 15, 35),
                "config.getMode()",
                unknownRes,
                Optional.empty(),
                AnalysisConfidence.HIGH,
                Map.of()
        );

        CryptoInventory inventory = engine.buildInventory(List.of(unknownUsage), Path.of("."));

        assertThat(inventory.assets()).hasSize(1);
        CryptoAsset asset = inventory.assets().get(0);

        assertThat(asset.isResolved()).isFalse();
        assertThat(asset.name()).isEqualTo("UNKNOWN-CIPHER");
        assertThat(asset.algorithmFamily()).isEqualTo("UNKNOWN");
        assertThat(asset.confidence()).isEqualTo(AnalysisConfidence.LOW);
        assertThat(asset.usages()).hasSize(1);
        assertThat(asset.usages().get(0).originalArgument()).isEqualTo("config.getMode()");

        assertThat(inventory.summary().unresolvedObservations()).isEqualTo(1);
        assertThat(inventory.summary().resolvedAssets()).isEqualTo(0);
    }

    @Test
    @DisplayName("Generates stable, machine-independent asset identifiers")
    void testStableAssetIdentifiers() {
        Path file = Path.of("src/App.java");
        CryptoApiUsage usage = createUsage(file, 10, 5, CryptoApiOwner.MESSAGE_DIGEST, "SHA-256");

        CryptoInventory inventory = engine.buildInventory(List.of(usage), Path.of("."));

        CryptoAsset asset = inventory.assets().get(0);
        CryptoAsset relocated = engine.buildInventory(List.of(createUsage(Path.of("other/App.java"),
                42, 9, CryptoApiOwner.MESSAGE_DIGEST, "sha-256")), Path.of("other")).assets().get(0);
        assertThat(asset.assetId()).isEqualTo(relocated.assetId()).startsWith("crypto-asset-");
        assertThat(asset.assetId()).doesNotContain("\\");
        assertThat(asset.assetId()).doesNotContain(":");
    }

    @Test
    @DisplayName("Returns empty inventory cleanly when no usages are provided")
    void testEmptyInventory() {
        CryptoInventory inventory = engine.buildInventory(List.of(), Path.of("."));

        assertThat(inventory.assets()).isEmpty();
        assertThat(inventory.relationships()).isEmpty();
        assertThat(inventory.summary().totalObservations()).isEqualTo(0);
        assertThat(inventory.summary().uniqueAssets()).isEqualTo(0);
    }

    @Test
    @DisplayName("Sorts assets deterministically (resolved before unresolved, then by family and name)")
    void testDeterministicSorting() {
        Path file = Path.of("src/App.java");

        CryptoApiUsage uTls = createUsage(file, 10, 5, CryptoApiOwner.SSL_CONTEXT, "TLSv1.3");
        CryptoApiUsage uSha = createUsage(file, 20, 5, CryptoApiOwner.MESSAGE_DIGEST, "SHA-256");
        CryptoApiUsage uAes = createUsage(file, 30, 5, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding");

        CryptoInventory inventory = engine.buildInventory(List.of(uTls, uSha, uAes), Path.of("."));

        List<String> names = inventory.assets().stream().map(CryptoAsset::name).toList();
        assertThat(names).containsExactly("AES/GCM/NoPadding", "SHA-256", "TLSv1.3");
    }

    @Test
    @DisplayName("Deduplicates identical usage occurrences at the exact same source location")
    void testDuplicateUsageDeduplication() {
        Path file = Path.of("src/App.java");
        CryptoApiUsage usage1 = createUsage(file, 15, 8, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding");
        CryptoApiUsage usage2 = createUsage(file, 15, 8, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding");

        CryptoInventory inventory = engine.buildInventory(List.of(usage1, usage2), Path.of("."));

        assertThat(inventory.assets()).hasSize(1);
        CryptoAsset asset = inventory.assets().get(0);
        assertThat(asset.usages()).hasSize(1);
        assertThat(asset.usageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Preserves enclosing class, enclosing method, and confidence mappings")
    void testEnclosingClassAndConfidenceMappings() {
        Path file = Path.of("src/SecurityService.java");
        CryptoAlgorithm algo = CryptoAlgorithm.parse("SHA-256");
        ExpressionResolution res = ExpressionResolution.resolved("SHA-256", "\"SHA-256\"", "Direct literal");
        CryptoApiUsage usage = new CryptoApiUsage(
                CryptoApiOwner.MESSAGE_DIGEST,
                "getInstance",
                file,
                new SourceRange(42, 10, 42, 35),
                "\"SHA-256\"",
                res,
                Optional.of(algo),
                AnalysisConfidence.HIGH,
                Map.of(
                        "enclosingClass", "com.example.SecurityService",
                        "enclosingMethod", "hashAuthToken"
                )
        );

        CryptoInventory inventory = engine.buildInventory(List.of(usage), Path.of("."));

        assertThat(inventory.assets()).hasSize(1);
        CryptoAsset asset = inventory.assets().get(0);
        assertThat(asset.confidence()).isEqualTo(AnalysisConfidence.HIGH);

        CryptoAssetUsage assetUsage = asset.usages().get(0);
        assertThat(assetUsage.enclosingClass()).contains("com.example.SecurityService");
        assertThat(assetUsage.enclosingMethod()).contains("hashAuthToken");
        assertThat(assetUsage.confidence()).isEqualTo(AnalysisConfidence.HIGH);
    }

    @Test
    @DisplayName("Classifies asymmetric RSA cipher as PUBLIC_KEY_ENCRYPTION primitive type")
    void testRsaAsymmetricCipherClassification() {
        Path file = Path.of("src/App.java");
        CryptoApiUsage usage = createUsage(file, 10, 5, CryptoApiOwner.CIPHER, "RSA/ECB/OAEPWithSHA-512AndMGF1Padding");

        CryptoInventory inventory = engine.buildInventory(List.of(usage), Path.of("."));

        assertThat(inventory.assets()).hasSize(1);
        CryptoAsset asset = inventory.assets().get(0);
        assertThat(asset.primitiveType()).isEqualTo(CryptoPrimitiveType.PUBLIC_KEY_ENCRYPTION);
        assertThat(asset.algorithmFamily()).isEqualTo("RSA");
        assertThat(asset.isResolved()).isTrue();
    }

    private CryptoApiUsage createUsage(Path file, int line, int col, CryptoApiOwner owner, String rawAlgo) {
        CryptoAlgorithm algo = CryptoAlgorithm.parse(rawAlgo);
        ExpressionResolution res = ExpressionResolution.resolved(rawAlgo, "\"" + rawAlgo + "\"", "Direct literal");
        return new CryptoApiUsage(
                owner,
                "getInstance",
                file,
                new SourceRange(line, col, line, col + rawAlgo.length()),
                "\"" + rawAlgo + "\"",
                res,
                Optional.of(algo),
                AnalysisConfidence.HIGH,
                Map.of()
        );
    }
}

