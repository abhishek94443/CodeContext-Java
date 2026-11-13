package com.codecontext.cli.export.cbom;

import com.codecontext.core.security.inventory.CryptoAsset;
import com.codecontext.core.security.inventory.CryptoAssetUsage;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Exporter generating standardized CycloneDX 1.6 Cryptography Bill of Materials (CBOM).
 * Conforms to the official CycloneDX 1.6 JSON Schema specification and cryptographic asset models.
 */
public class CycloneDxCbomExporter {

    public static final String CYCLONEDX_SPEC_VERSION = "1.6";
    public static final String CYCLONEDX_SCHEMA = "http://cyclonedx.org/schema/bom-1.6.schema.json";
    public static final String TOOL_NAME = "CodeContext";
    public static final String TOOL_VERSION = "1.0.0";

    private final ObjectMapper mapper;

    public CycloneDxCbomExporter() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Exports a CycloneDX 1.6 CBOM JSON file.
     *
     * @param inventory  The canonical cryptographic inventory.
     * @param repoRoot   The root directory of the analyzed repository.
     * @param outputPath The target output file path (e.g. crypto-bom.json).
     * @throws IOException If file writing fails.
     */
    public void exportCbom(CryptoInventory inventory, Path repoRoot, Path outputPath) throws IOException {
        Objects.requireNonNull(inventory, "inventory cannot be null");
        Objects.requireNonNull(repoRoot, "repoRoot cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        String json = generateCbomJson(inventory, repoRoot);
        Files.writeString(outputPath, json, StandardCharsets.UTF_8);
    }

    /**
     * Generates a valid CycloneDX 1.6 CBOM JSON string from a canonical inventory.
     */
    public String generateCbomJson(CryptoInventory inventory, Path repoRoot) {
        Objects.requireNonNull(inventory, "inventory cannot be null");
        Objects.requireNonNull(repoRoot, "repoRoot cannot be null");

        String repoName = repoRoot.getFileName() != null ? repoRoot.getFileName().toString() : "root";
        String rootRef = "pkg:generic/" + repoName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-") + "@1.0.0";

        // Deterministic serial number based on repo name and spec version
        UUID serialUuid = UUID.nameUUIDFromBytes((repoName + "-cyclonedx-1.6-cbom").getBytes(StandardCharsets.UTF_8));

        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", CYCLONEDX_SCHEMA);
        root.put("bomFormat", "CycloneDX");
        root.put("specVersion", CYCLONEDX_SPEC_VERSION);
        root.put("serialNumber", "urn:uuid:" + serialUuid);
        root.put("version", 1);

        // Metadata
        ObjectNode metadata = root.putObject("metadata");
        metadata.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        // Tools
        ObjectNode tools = metadata.putObject("tools");
        ArrayNode toolComponents = tools.putArray("components");
        ObjectNode toolComp = toolComponents.addObject();
        toolComp.put("type", "application");
        toolComp.put("name", TOOL_NAME);
        toolComp.put("version", TOOL_VERSION);
        toolComp.put("description", "CodeContext Cryptographic Intelligence Platform");

        // Target Root Component
        ObjectNode targetComponent = metadata.putObject("component");
        targetComponent.put("type", "application");
        targetComponent.put("bom-ref", rootRef);
        targetComponent.put("name", repoName);
        targetComponent.put("version", "1.0.0");

        // Components (Cryptographic Assets)
        ArrayNode components = root.putArray("components");
        ArrayNode rootDependsOn = mapper.createArrayNode();

        for (CryptoAsset asset : inventory.assets()) {
            ObjectNode comp = components.addObject();
            comp.put("type", "cryptographic-asset");
            comp.put("bom-ref", asset.assetId());
            comp.put("name", asset.name());

            rootDependsOn.add(asset.assetId());

            // Native CycloneDX 1.6 cryptoProperties
            ObjectNode cryptoProps = comp.putObject("cryptoProperties");
            String assetType = asset.primitiveType().cycloneDxName().equals("protocol") ? "protocol" : "algorithm";
            cryptoProps.put("assetType", assetType);

            ObjectNode algoProps = cryptoProps.putObject("algorithmProperties");
            algoProps.put("primitive", asset.primitiveType().cycloneDxName());

            asset.mode().ifPresent(m -> algoProps.put("mode", normalizeMode(m)));
            asset.padding().ifPresent(p -> algoProps.put("padding", normalizePadding(p)));

            ArrayNode cryptoFunctions = algoProps.putArray("cryptoFunctions");
            for (String func : determineCryptoFunctions(asset)) {
                cryptoFunctions.add(func);
            }

            // Evidence (Occurrences)
            if (!asset.usages().isEmpty()) {
                ObjectNode evidence = comp.putObject("evidence");
                ArrayNode occurrences = evidence.putArray("occurrences");
                for (CryptoAssetUsage u : asset.usages()) {
                    ObjectNode occ = occurrences.addObject();
                    occ.put("location", u.filePath().toString().replace('\\', '/'));
                    occ.put("line", u.location().startLine());
                    occ.put("offset", u.location().startColumn());
                }
            }
        }

        // Dependencies
        ArrayNode dependencies = root.putArray("dependencies");
        ObjectNode rootDep = dependencies.addObject();
        rootDep.put("ref", rootRef);
        rootDep.set("dependsOn", rootDependsOn);

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize CycloneDX 1.6 CBOM JSON", e);
        }
    }

    private String normalizeMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) return "unknown";
        String lower = rawMode.toLowerCase(Locale.ROOT).trim();
        return switch (lower) {
            case "cbc", "ecb", "ccm", "gcm", "cfb", "ofb", "ctr" -> lower;
            default -> "other";
        };
    }

    private String normalizePadding(String rawPadding) {
        if (rawPadding == null || rawPadding.isBlank()) return "unknown";
        String lower = rawPadding.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("nopadding") || lower.equals("none") || lower.equals("raw")) {
            return "raw";
        }
        if (lower.contains("pkcs5")) {
            return "pkcs5";
        }
        if (lower.contains("pkcs7")) {
            return "pkcs7";
        }
        if (lower.contains("pkcs1")) {
            return "pkcs1v15";
        }
        if (lower.contains("oaep")) {
            return "oaep";
        }
        return "other";
    }

    private String[] determineCryptoFunctions(CryptoAsset asset) {
        return switch (asset.primitiveType()) {
            case BLOCK_CIPHER, STREAM_CIPHER -> new String[]{"encrypt", "decrypt"};
            case MESSAGE_DIGEST -> new String[]{"digest"};
            case SIGNATURE -> new String[]{"sign", "verify"};
            case MAC -> new String[]{"tag", "digest"};
            case KEY_AGREEMENT -> new String[]{"key-agree"};
            case KEY_GENERATOR, KEY_PAIR_GENERATOR -> new String[]{"generate"};
            case SECURE_RANDOM -> new String[]{"generate"};
            case TLS_PROTOCOL -> new String[]{"protocol"};
            case OTHER -> new String[]{"other"};
        };
    }
}
