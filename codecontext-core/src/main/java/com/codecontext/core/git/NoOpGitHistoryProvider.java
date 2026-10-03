package com.codecontext.core.git;

import java.nio.file.Path;
import java.util.Map;

/**
 * Graceful fallback when .git is absent (CI containers, shallow clones, zip exports).
 * Adheres to ADR-006.
 */
public class NoOpGitHistoryProvider implements GitHistoryProvider {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Map<String, GitEvolutionMetric> extractMetrics(Path repoRoot, int daysWindow) {
        return Map.of();
    }
}
