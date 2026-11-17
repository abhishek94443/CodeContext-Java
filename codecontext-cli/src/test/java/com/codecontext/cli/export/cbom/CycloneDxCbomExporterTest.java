package com.codecontext.cli.export.cbom;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.crypto.CryptoAlgorithm;
import com.codecontext.core.security.crypto.CryptoApiOwner;
import com.codecontext.core.security.crypto.CryptoApiUsage;
import com.codecontext.core.security.crypto.ExpressionResolution;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoInventoryEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CycloneDxCbomExporterTest {

    @Test
    void tlsUsesProtocolPropertiesAndValidSchema(@TempDir Path repo) throws Exception {
        var inventory = new CryptoInventoryEngine().buildInventory(List.of(
                createUsage(repo.resolve("A.java"), 1, 1, CryptoApiOwner.SSL_CONTEXT, "TLSv1.3")), repo);
        var root = new ObjectMapper().readTree(new CycloneDxCbomExporter().generateCbomJson(inventory, repo));
        validatePinnedSchema(root);
        var properties = root.path("components").get(0).path("cryptoProperties");
        assertThat(properties.path("assetType").asText()).isEqualTo("protocol");
        assertThat(properties.path("protocolProperties").path("type").asText()).isEqualTo("tls");
        assertThat(properties.has("algorithmProperties")).isFalse();
    }

    @Test
    void keyAgreementUsesValidFunctions(@TempDir Path repo) throws Exception {
        var inventory = new CryptoInventoryEngine().buildInventory(List.of(
                createUsage(repo.resolve("A.java"), 1, 1, CryptoApiOwner.KEY_AGREEMENT, "ECDH")), repo);
        validatePinnedSchema(new ObjectMapper().readTree(new CycloneDxCbomExporter().generateCbomJson(inventory, repo)));
    }

    @Test
    void distinctAesOperationsHaveUniqueStableReferences(@TempDir Path repo) throws Exception {
        var observations = List.of(
                createUsage(repo.resolve("A.java"), 1, 1, CryptoApiOwner.CIPHER, "AES"),
                createUsage(repo.resolve("A.java"), 2, 1, CryptoApiOwner.KEY_GENERATOR, "AES"));
        var inventory = new CryptoInventoryEngine().buildInventory(observations, repo);
        assertThat(inventory.assets()).hasSize(2);
        assertThat(inventory.assets()).extracting(a -> a.assetId()).doesNotHaveDuplicates();
        var root = new ObjectMapper().readTree(new CycloneDxCbomExporter().generateCbomJson(inventory, repo));
        var refs = new java.util.HashSet<String>();
        root.path("components").forEach(c -> refs.add(c.path("bom-ref").asText()));
        assertThat(refs).hasSize(2);
        root.path("dependencies").get(0).path("dependsOn").forEach(r -> assertThat(refs).contains(r.asText()));
        var elsewhere = new CryptoInventoryEngine().buildInventory(List.of(
                createUsage(Path.of("elsewhere/A.java"), 9, 1, CryptoApiOwner.CIPHER, "aes"),
                createUsage(Path.of("elsewhere/A.java"), 10, 1, CryptoApiOwner.KEY_GENERATOR, "AES")), Path.of("elsewhere"));
        assertThat(elsewhere.assets()).extracting(a -> a.assetId()).containsExactlyInAnyOrderElementsOf(refs);
    }

    private void validatePinnedSchema(JsonNode root) throws Exception {
        try (var stream = getClass().getResourceAsStream("/cyclonedx/bom-1.6.schema.json")) {
            assertThat(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(stream).validate(root)).isEmpty();
        }
    }

    private final CycloneDxCbomExporter exporter = new CycloneDxCbomExporter();
    private final CryptoInventoryEngine inventoryEngine = new CryptoInventoryEngine();

    @Test
    @DisplayName("Generates valid CycloneDX 1.6 CBOM and validates against official JSON schema")
    void testValidCbomGenerationAndOfficialSchemaValidation(@TempDir Path tempRepo) throws Exception {
        Path file = tempRepo.resolve("src/CryptoService.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class CryptoService {}");

        CryptoApiUsage aesGcm = createUsage(file, 10, 8, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding");
        CryptoApiUsage desEcb = createUsage(file, 20, 8, CryptoApiOwner.CIPHER, "DES/ECB/PKCS5Padding");
        CryptoApiUsage sha256 = createUsage(file, 30, 8, CryptoApiOwner.MESSAGE_DIGEST, "SHA-256");

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(aesGcm, desEcb, sha256), tempRepo);

        Path cbomOutput = tempRepo.resolve("crypto-bom.json");
        exporter.exportCbom(inventory, tempRepo, cbomOutput);

        assertThat(Files.exists(cbomOutput)).isTrue();
        String json = Files.readString(cbomOutput);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // 1. Structure assertions
        assertThat(root.get("bomFormat").asText()).isEqualTo("CycloneDX");
        assertThat(root.get("specVersion").asText()).isEqualTo("1.6");
        assertThat(root.get("serialNumber").asText()).startsWith("urn:uuid:");

        JsonNode components = root.get("components");
        assertThat(components.size()).isEqualTo(3);

        JsonNode c0 = components.get(0);
        assertThat(c0.get("type").asText()).isEqualTo("cryptographic-asset");
        assertThat(c0.get("name").asText()).isNotEmpty();
        assertThat(c0.has("cryptoProperties")).isTrue();
        assertThat(c0.get("cryptoProperties").get("assetType").asText()).isIn("algorithm", "protocol");
        assertThat(c0.get("cryptoProperties").get("algorithmProperties").get("primitive").asText()).isNotEmpty();

        JsonNode evidence = c0.get("evidence");
        assertThat(evidence.has("occurrences")).isTrue();
        JsonNode occ = evidence.get("occurrences").get(0);
        assertThat(occ.get("location").asText()).isEqualTo("src/CryptoService.java");
        assertThat(occ.get("line").asInt()).isGreaterThanOrEqualTo(1);

        // 2. Official CycloneDX 1.6 Schema Validation
        try (InputStream schemaStream = getClass().getResourceAsStream("/cyclonedx/bom-1.6.schema.json")) {
            assertThat(schemaStream).isNotNull();
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaStream);

            Set<ValidationMessage> errors = schema.validate(root);
            if (!errors.isEmpty()) {
                System.out.println("VALIDATION ERRORS:\n" + errors);
            }
            assertThat(errors).as("CycloneDX 1.6 official schema validation errors: " + errors).isEmpty();
        }
    }

    @Test
    @DisplayName("Empty inventory exports a schema-compliant CycloneDX 1.6 CBOM with 0 components")
    void testEmptyInventoryCbomValidation(@TempDir Path tempRepo) throws Exception {
        CryptoInventory emptyInventory = CryptoInventory.empty();
        Path cbomOutput = tempRepo.resolve("empty-bom.json");
        exporter.exportCbom(emptyInventory, tempRepo, cbomOutput);

        String json = Files.readString(cbomOutput);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertThat(root.get("components").size()).isEqualTo(0);

        try (InputStream schemaStream = getClass().getResourceAsStream("/cyclonedx/bom-1.6.schema.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaStream);
            Set<ValidationMessage> errors = schema.validate(root);
            assertThat(errors).isEmpty();
        }
    }

    @Test
    @DisplayName("Unresolved cryptographic assets export schema-compliant CBOM preserving unknown status")
    void testUnresolvedInventoryCbomValidation(@TempDir Path tempRepo) throws Exception {
        Path file = tempRepo.resolve("src/Dynamic.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Dynamic {}");

        ExpressionResolution unk = ExpressionResolution.unknown("config.get()", "Dynamic call");
        CryptoApiUsage usage = new CryptoApiUsage(
                CryptoApiOwner.CIPHER,
                "getInstance",
                file,
                new SourceRange(5, 1, 5, 20),
                "config.get()",
                unk,
                Optional.empty(),
                AnalysisConfidence.HIGH,
                Map.of()
        );

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), tempRepo);
        String json = exporter.generateCbomJson(inventory, tempRepo);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertThat(root.get("components").get(0).get("name").asText()).isEqualTo("UNKNOWN-CIPHER");

        try (InputStream schemaStream = getClass().getResourceAsStream("/cyclonedx/bom-1.6.schema.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaStream);
            Set<ValidationMessage> errors = schema.validate(root);
            assertThat(errors).isEmpty();
        }
    }

    @Test
    @DisplayName("Asymmetric RSA cipher exports valid schema-compliant CBOM with pke primitive and encrypt/decrypt functions")
    void testRsaCbomSchemaValidation(@TempDir Path tempRepo) throws Exception {
        Path file = tempRepo.resolve("src/RsaService.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class RsaService {}");

        CryptoApiUsage rsaUsage = createUsage(file, 15, 8, CryptoApiOwner.CIPHER, "RSA/ECB/OAEPWithSHA-512AndMGF1Padding");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(rsaUsage), tempRepo);

        Path cbomOutput = tempRepo.resolve("rsa-bom.json");
        exporter.exportCbom(inventory, tempRepo, cbomOutput);

        assertThat(Files.exists(cbomOutput)).isTrue();
        String json = Files.readString(cbomOutput);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        JsonNode comp = root.get("components").get(0);
        assertThat(comp.get("name").asText()).isEqualTo("RSA/ECB/OAEPWithSHA-512AndMGF1Padding");
        JsonNode algoProps = comp.get("cryptoProperties").get("algorithmProperties");
        assertThat(algoProps.get("primitive").asText()).isEqualTo("pke");
        JsonNode funcs = algoProps.get("cryptoFunctions");
        assertThat(funcs.toString()).contains("encrypt").contains("decrypt");

        try (InputStream schemaStream = getClass().getResourceAsStream("/cyclonedx/bom-1.6.schema.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaStream);
            Set<ValidationMessage> errors = schema.validate(root);
            assertThat(errors).isEmpty();
        }
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
