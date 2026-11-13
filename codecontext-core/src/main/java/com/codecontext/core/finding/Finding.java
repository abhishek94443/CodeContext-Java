package com.codecontext.core.finding;

import com.codecontext.core.model.SourceRange;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a static analysis or architectural finding.
 * Independent of presentation and output formats (such as SARIF or CLI terminal output).
 */
public record Finding(
    String ruleId,
    String ruleName,
    FindingCategory category,
    FindingSeverity severity,
    String message,
    Optional<Path> filePath,
    Optional<SourceRange> location,
    String evidence,
    String remediation,
    Optional<String> cweId,
    AnalysisConfidence confidence,
    Map<String, String> properties
) implements Comparable<Finding> {

    public Finding {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        filePath = filePath == null ? Optional.empty() : filePath;
        location = location == null ? Optional.empty() : location;
        evidence = evidence == null ? "" : evidence;
        remediation = remediation == null ? "" : remediation;
        cweId = cweId == null ? Optional.empty() : cweId;
        confidence = confidence == null ? AnalysisConfidence.HIGH : confidence;
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Stable identity representation used for unambiguous deduplication across multiple analysis passes.
     */
    public record FindingIdentity(
            String ruleId,
            String normalizedPath,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn,
            String occurrenceIdentity
    ) {}

    /**
     * Computes the stable identity for this finding.
     */
    public FindingIdentity identity() {
        String normPath = filePath.map(p -> p.toString().replace('\\', '/')).orElse("");
        int sl = location.map(SourceRange::startLine).orElse(0);
        int sc = location.map(SourceRange::startColumn).orElse(0);
        int el = location.map(SourceRange::endLine).orElse(0);
        int ec = location.map(SourceRange::endColumn).orElse(0);
        String occurrence = !evidence.isBlank() ? evidence : message;
        return new FindingIdentity(ruleId, normPath, sl, sc, el, ec, occurrence);
    }

    /**
     * Explicitly deduplicates a collection of findings based on stable finding identity,
     * preserving the earliest encountered instance, and returns a deterministically sorted list.
     */
    public static List<Finding> deduplicate(Collection<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }
        Map<FindingIdentity, Finding> uniqueMap = new LinkedHashMap<>();
        for (Finding f : findings) {
            if (f != null) {
                uniqueMap.putIfAbsent(f.identity(), f);
            }
        }
        List<Finding> deduped = new ArrayList<>(uniqueMap.values());
        Collections.sort(deduped);
        return Collections.unmodifiableList(deduped);
    }

    @Override
    public int compareTo(Finding other) {
        if (other == null) {
            return 1;
        }

        // 1. File path (POSIX representation)
        String thisPath = this.filePath.map(p -> p.toString().replace('\\', '/')).orElse("");
        String otherPath = other.filePath.map(p -> p.toString().replace('\\', '/')).orElse("");
        int pathCmp = thisPath.compareTo(otherPath);
        if (pathCmp != 0) {
            return pathCmp;
        }

        // 2. Start line
        int thisLine = this.location.map(SourceRange::startLine).orElse(0);
        int otherLine = other.location.map(SourceRange::startLine).orElse(0);
        int lineCmp = Integer.compare(thisLine, otherLine);
        if (lineCmp != 0) {
            return lineCmp;
        }

        // 3. Start column
        int thisCol = this.location.map(SourceRange::startColumn).orElse(0);
        int otherCol = other.location.map(SourceRange::startColumn).orElse(0);
        int colCmp = Integer.compare(thisCol, otherCol);
        if (colCmp != 0) {
            return colCmp;
        }

        // 4. Rule ID
        int ruleCmp = this.ruleId.compareTo(other.ruleId);
        if (ruleCmp != 0) {
            return ruleCmp;
        }

        // 5. Severity (higher severity first, or ordinal)
        int sevCmp = this.severity.compareTo(other.severity);
        if (sevCmp != 0) {
            return sevCmp;
        }

        // 6. Message
        return this.message.compareTo(other.message);
    }

    public static class Builder {
        private String ruleId;
        private String ruleName;
        private FindingCategory category = FindingCategory.OTHER;
        private FindingSeverity severity = FindingSeverity.MEDIUM;
        private String message;
        private Path filePath;
        private SourceRange location;
        private String evidence = "";
        private String remediation = "";
        private String cweId;
        private AnalysisConfidence confidence = AnalysisConfidence.HIGH;
        private final Map<String, String> properties = new LinkedHashMap<>();

        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        public Builder ruleName(String ruleName) {
            this.ruleName = ruleName;
            return this;
        }

        public Builder rule(RuleMetadata meta) {
            if (meta != null) {
                this.ruleId = meta.id();
                this.ruleName = meta.name();
                this.category = meta.category();
                this.severity = meta.defaultSeverity();
                meta.cweId().ifPresent(cwe -> this.cweId = cwe);
            }
            return this;
        }

        public Builder category(FindingCategory category) {
            this.category = category;
            return this;
        }

        public Builder severity(FindingSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder filePath(Path filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder location(SourceRange location) {
            this.location = location;
            return this;
        }

        public Builder evidence(String evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder remediation(String remediation) {
            this.remediation = remediation;
            return this;
        }

        public Builder cweId(String cweId) {
            this.cweId = cweId;
            return this;
        }

        public Builder confidence(AnalysisConfidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder property(String key, String value) {
            if (key != null && value != null) {
                this.properties.put(key, value);
            }
            return this;
        }

        public Finding build() {
            return new Finding(
                ruleId,
                ruleName,
                category,
                severity,
                message,
                Optional.ofNullable(filePath),
                Optional.ofNullable(location),
                evidence,
                remediation,
                Optional.ofNullable(cweId),
                confidence,
                properties
            );
        }
    }
}

