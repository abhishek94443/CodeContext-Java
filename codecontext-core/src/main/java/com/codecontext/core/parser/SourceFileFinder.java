package com.codecontext.core.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * High-performance file finder that traverses repository directories and filters Java source files.
 * Ignores build outputs, version control directories, dependency caches, and test sources by default (CR-S3.1-01).
 */
public final class SourceFileFinder {

    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
        ".git",
        ".github",
        ".gradle",
        "build",
        "target",
        "out",
        "node_modules",
        "dist",
        ".idea",
        ".vscode"
    );

    private SourceFileFinder() {}

    /**
     * Finds all production .java source files within the given root directory, filtering out tests and ignored folders.
     */
    public static List<Path> find(Path rootPath) {
        return find(rootPath, false);
    }

    /**
     * Finds .java source files within the root directory, with optional inclusion of test classes.
     */
    public static List<Path> find(Path rootPath, boolean includeTests) {
        if (rootPath == null || !Files.exists(rootPath)) {
            throw new IllegalArgumentException("Path does not exist: " + rootPath);
        }

        try (Stream<Path> stream = Files.walk(rootPath)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(SourceFileFinder::isNotIgnored)
                .filter(path -> includeTests || !isTestFile(path))
                .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan directory tree: " + rootPath, e);
        }
    }

    public static boolean isTestFile(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith("Test.java") || fileName.endsWith("Tests.java")
                || fileName.endsWith("TestCase.java") || fileName.endsWith("IT.java")) {
            return true;
        }
        for (Path component : path) {
            String name = component.toString().toLowerCase(Locale.ROOT);
            if (name.equals("test") || name.equals("tests")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotIgnored(Path path) {
        for (Path component : path) {
            if (IGNORED_DIRECTORY_NAMES.contains(component.toString())) {
                return false;
            }
        }
        return true;
    }
}
