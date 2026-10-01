package com.codecontext.core.model;

/**
 * Immutable source code coordinate range (1-based index).
 */
public record SourceRange(
    int startLine,
    int startColumn,
    int endLine,
    int endColumn
) {
    public SourceRange {
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1, was: " + startLine);
        }
        if (startColumn < 1) {
            throw new IllegalArgumentException("startColumn must be >= 1, was: " + startColumn);
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException("endLine cannot be less than startLine. startLine=" + startLine + ", endLine=" + endLine);
        }
        if (startLine == endLine && endColumn < startColumn) {
            throw new IllegalArgumentException("endColumn cannot be less than startColumn on the same line. startColumn=" + startColumn + ", endColumn=" + endColumn);
        }
    }
}