package com.codecontext.core.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileFinderTest {

    @Test
    @DisplayName("UTC-S1.1-US03-001: Discovers .java files in nested subfolders")
    void shouldDiscoverJavaFilesInNestedFolders(@TempDir Path tempDir) throws IOException {
        Path subDir = Files.createDirectories(tempDir.resolve("src/main/java/com/pkg"));
        Path javaFile = Files.createFile(subDir.resolve("Service.java"));

        List<Path> files = SourceFileFinder.find(tempDir);

        assertThat(files).hasSize(1).contains(javaFile);
    }

    @Test
    @DisplayName("UTC-S1.1-US03-002: Skips non-java files")
    void shouldSkipNonJavaFiles(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("Service.java"));
        Files.createFile(tempDir.resolve("README.md"));
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createFile(tempDir.resolve("Service.kt"));
        Files.createFile(tempDir.resolve("script.py"));

        List<Path> files = SourceFileFinder.find(tempDir);

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("Service.java");
    }

    @Test
    @DisplayName("UTC-S1.1-US03-003 / FTC-S1.1-010: Skips ignored folder hierarchies")
    void shouldSkipIgnoredDirectories(@TempDir Path tempDir) throws IOException {
        // Valid file
        Path validDir = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path validFile = Files.createFile(validDir.resolve("App.java"));

        // Ignored files in standard build/cache directories
        Path gitDir = Files.createDirectories(tempDir.resolve(".git"));
        Files.createFile(gitDir.resolve("Head.java"));

        Path buildDir = Files.createDirectories(tempDir.resolve("build/classes"));
        Files.createFile(buildDir.resolve("BuildClass.java"));

        Path targetDir = Files.createDirectories(tempDir.resolve("target/classes"));
        Files.createFile(targetDir.resolve("TargetClass.java"));

        Path gradleDir = Files.createDirectories(tempDir.resolve(".gradle/caches"));
        Files.createFile(gradleDir.resolve("Cache.java"));

        Path nodeDir = Files.createDirectories(tempDir.resolve("node_modules/pkg"));
        Files.createFile(nodeDir.resolve("Node.java"));

        List<Path> files = SourceFileFinder.find(tempDir);

        assertThat(files).hasSize(1).contains(validFile);
    }

    @Test
    @DisplayName("UTC-S1.1-US03-004: Non-existent path handling throws IllegalArgumentException")
    void shouldThrowForNonExistentPath() {
        Path nonExistent = Path.of("non/existent/path/for/test/xyz");

        assertThatThrownBy(() -> SourceFileFinder.find(nonExistent))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Path does not exist");
    }
    @Test
    @DisplayName("UTC-S3.1-CR01-001: [CR-S3.1-01] SourceFileFinder excludes test files and directories by default")
    void shouldExcludeTestFilesByDefault(@TempDir Path tempDir) throws IOException {
        Path mainDir = Files.createDirectories(tempDir.resolve("src/main/java/com/pkg"));
        Path testDir = Files.createDirectories(tempDir.resolve("src/test/java/com/pkg"));

        Path prodFile = Files.createFile(mainDir.resolve("Service.java"));
        Files.createFile(testDir.resolve("ServiceTest.java"));
        Files.createFile(mainDir.resolve("ServiceIT.java"));
        Files.createFile(mainDir.resolve("CustomTestCase.java"));

        List<Path> files = SourceFileFinder.find(tempDir);

        assertThat(files).hasSize(1).containsExactly(prodFile);
    }

    @Test
    @DisplayName("UTC-S3.1-CR01-002: [CR-S3.1-01] SourceFileFinder includes test files when includeTests is true")
    void shouldIncludeTestFilesWhenRequested(@TempDir Path tempDir) throws IOException {
        Path mainDir = Files.createDirectories(tempDir.resolve("src/main/java/com/pkg"));
        Path testDir = Files.createDirectories(tempDir.resolve("src/test/java/com/pkg"));

        Path prodFile = Files.createFile(mainDir.resolve("Service.java"));
        Path testFile = Files.createFile(testDir.resolve("ServiceTest.java"));

        List<Path> files = SourceFileFinder.find(tempDir, true);

        assertThat(files).hasSize(2).contains(prodFile, testFile);
    }
}