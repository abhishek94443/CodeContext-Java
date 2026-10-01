package com.codecontext.core.parser;

import java.nio.file.Path;
import java.util.List;

/**
 * Summary metrics of a Pass 1 directory scan.
 */
public record ScanSummary(
    int totalFilesScanned,
    int totalTypesDiscovered,
    List<Path> failedFiles
) {
    public ScanSummary {
        failedFiles = failedFiles == null ? List.of() : List.copyOf(failedFiles);
    }
}