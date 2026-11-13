package com.codecontext.core.finding;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable metadata describing an analysis rule.
 */
public record RuleMetadata(
    String id,
    String name,
    FindingCategory category,
    FindingSeverity defaultSeverity,
    String shortDescription,
    String fullDescription,
    Optional<String> helpUri,
    Optional<String> cweId
) {
    public RuleMetadata {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(defaultSeverity, "defaultSeverity cannot be null");
        Objects.requireNonNull(shortDescription, "shortDescription cannot be null");
        fullDescription = fullDescription == null ? shortDescription : fullDescription;
        helpUri = helpUri == null ? Optional.empty() : helpUri;
        cweId = cweId == null ? Optional.empty() : cweId;
    }

    public static RuleMetadata of(
        String id,
        String name,
        FindingCategory category,
        FindingSeverity defaultSeverity,
        String shortDescription,
        String fullDescription
    ) {
        return new RuleMetadata(
            id,
            name,
            category,
            defaultSeverity,
            shortDescription,
            fullDescription,
            Optional.empty(),
            Optional.empty()
        );
    }

    public static RuleMetadata of(
        String id,
        String name,
        FindingCategory category,
        FindingSeverity defaultSeverity,
        String shortDescription,
        String fullDescription,
        String helpUri,
        String cweId
    ) {
        return new RuleMetadata(
            id,
            name,
            category,
            defaultSeverity,
            shortDescription,
            fullDescription,
            Optional.ofNullable(helpUri),
            Optional.ofNullable(cweId)
        );
    }
}

