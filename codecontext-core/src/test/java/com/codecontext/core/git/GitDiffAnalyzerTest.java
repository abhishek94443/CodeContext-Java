package com.codecontext.core.git;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitDiffAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("UTC-S3.3-US01-001a: should detect uncommitted modifications in working tree")
    void shouldDetectUncommittedModifications() throws Exception {
        // Initialize Git repo
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path file1 = tempDir.resolve("App.java");
            Files.writeString(file1, "public class App {}");
            git.add().addFilepattern("App.java").call();
            git.commit().setMessage("Initial commit").call();

            // Modify file1 and create file2
            Files.writeString(file1, "public class App { int x = 1; }");
            Path file2 = tempDir.resolve("Service.java");
            Files.writeString(file2, "public class Service {}");

            GitDiffAnalyzer analyzer = new GitDiffAnalyzer();
            var result = analyzer.analyzeDiff(tempDir, null);

            assertThat(result.isGitRepo()).isTrue();
            assertThat(result.modifiedFiles()).contains("App.java", "Service.java");
        }
    }

    @Test
    @DisplayName("UTC-S3.3-US01-001b: should report not a git repo when .git missing")
    void shouldReportNonGitRepo() {
        GitDiffAnalyzer analyzer = new GitDiffAnalyzer();
        var result = analyzer.analyzeDiff(tempDir, null);

        assertThat(result.isGitRepo()).isFalse();
        assertThat(result.modifiedFiles()).isEmpty();
    }
}
