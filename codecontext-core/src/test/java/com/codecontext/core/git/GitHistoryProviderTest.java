package com.codecontext.core.git;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHistoryProviderTest {

    @Test
    @DisplayName("UTC-S2.3-US01-001: GitEvolutionMetric valid construction")
    void should_create_valid_git_metric() {
        GitEvolutionMetric metric = new GitEvolutionMetric(
                "src/Main.java",
                10,
                150,
                25,
                3,
                0.25
        );

        assertThat(metric.filePath()).isEqualTo("src/Main.java");
        assertThat(metric.commitCount()).isEqualTo(10);
        assertThat(metric.linesAdded()).isEqualTo(150);
        assertThat(metric.linesDeleted()).isEqualTo(25);
        assertThat(metric.uniqueAuthors()).isEqualTo(3);
        assertThat(metric.busFactorRisk()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("UTC-S2.3-US01-002: Reject invalid metrics")
    void should_reject_invalid_metric_bounds() {
        assertThatThrownBy(() -> new GitEvolutionMetric("a", -1, 0, 0, 1, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitEvolutionMetric("a", 1, 0, 0, 1, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FTC-S2.3-002: Graceful non-git fallback via NoOpGitHistoryProvider")
    void should_gracefully_fallback_on_non_git_directory(@TempDir Path tempDir) {
        GitHistoryProvider provider = GitHistoryProviderFactory.createProvider(tempDir);

        assertThat(provider).isInstanceOf(NoOpGitHistoryProvider.class);
        assertThat(provider.isAvailable()).isFalse();

        Map<String, GitEvolutionMetric> metrics = provider.extractMetrics(tempDir, 90);
        assertThat(metrics).isEmpty();
    }

    @Test
    @DisplayName("FTC-S2.3-001: JGitHistoryProvider mines commits, additions, and Shannon entropy")
    void should_mine_git_history_correctly(@TempDir Path tempDir) throws Exception {
        // Initialize Git repo in temp directory
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Commit 1: Alice creates FileA
            Path fileA = tempDir.resolve("FileA.java");
            Files.writeString(fileA, "public class FileA {\n  void a() {}\n}\n");
            git.add().addFilepattern("FileA.java").call();
            git.commit().setMessage("Initial FileA").setAuthor("Alice", "alice@example.com").call();

            // Commit 2: Alice edits FileA
            Files.writeString(fileA, "public class FileA {\n  void a() {}\n  void a2() {}\n}\n");
            git.add().addFilepattern("FileA.java").call();
            git.commit().setMessage("Update FileA").setAuthor("Alice", "alice@example.com").call();

            // Commit 3: Bob creates FileB
            Path fileB = tempDir.resolve("FileB.java");
            Files.writeString(fileB, "public class FileB {}\n");
            git.add().addFilepattern("FileB.java").call();
            git.commit().setMessage("Initial FileB").setAuthor("Bob", "bob@example.com").call();

            // Commit 4: Charlie edits FileB
            Files.writeString(fileB, "public class FileB {\n  void b() {}\n}\n");
            git.add().addFilepattern("FileB.java").call();
            git.commit().setMessage("Update FileB").setAuthor("Charlie", "charlie@example.com").call();
        }

        JGitHistoryProvider provider = new JGitHistoryProvider();
        assertThat(provider.isAvailable()).isTrue();

        Map<String, GitEvolutionMetric> metrics = provider.extractMetrics(tempDir, 90);

        assertThat(metrics).containsKey("FileA.java");
        assertThat(metrics).containsKey("FileB.java");

        GitEvolutionMetric metricA = metrics.get("FileA.java");
        assertThat(metricA.commitCount()).isEqualTo(2);
        assertThat(metricA.uniqueAuthors()).isEqualTo(1);
        assertThat(metricA.busFactorRisk()).isEqualTo(1.0); // 1 author = maximum monopoly risk

        GitEvolutionMetric metricB = metrics.get("FileB.java");
        assertThat(metricB.commitCount()).isEqualTo(2);
        assertThat(metricB.uniqueAuthors()).isEqualTo(2);
        assertThat(metricB.busFactorRisk()).isLessThan(0.1); // 2 equal authors = low risk
    }
}
