package com.codecontext.core.git;

import java.nio.file.Path;
import java.util.Map;

/**
 * SPI for mining Git repository commit history and author entropy.
 */
public interface GitHistoryProvider {
    boolean isAvailable();
    Map<String, GitEvolutionMetric> extractMetrics(Path repoRoot, int daysWindow);
}
