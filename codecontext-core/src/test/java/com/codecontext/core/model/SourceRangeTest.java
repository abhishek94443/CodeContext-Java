package com.codecontext.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceRangeTest {

    @Test
    @DisplayName("UTC-S1.1-US01-001 / FTC-S1.1-001: Valid single-line range creation")
    void shouldCreateValidSingleLineRange() {
        SourceRange range = new SourceRange(1, 1, 1, 10);

        assertThat(range.startLine()).isEqualTo(1);
        assertThat(range.startColumn()).isEqualTo(1);
        assertThat(range.endLine()).isEqualTo(1);
        assertThat(range.endColumn()).isEqualTo(10);
    }

    @Test
    @DisplayName("UTC-S1.1-US01-002: Valid multi-line range creation")
    void shouldCreateValidMultiLineRange() {
        SourceRange range = new SourceRange(10, 5, 50, 20);

        assertThat(range.startLine()).isEqualTo(10);
        assertThat(range.startColumn()).isEqualTo(5);
        assertThat(range.endLine()).isEqualTo(50);
        assertThat(range.endColumn()).isEqualTo(20);
    }

    @Test
    @DisplayName("UTC-S1.1-US01-003: startLine zero or negative throws IllegalArgumentException")
    void shouldThrowWhenStartLineZeroOrNegative() {
        assertThatThrownBy(() -> new SourceRange(0, 1, 10, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("startLine must be >= 1");

        assertThatThrownBy(() -> new SourceRange(-5, 1, 10, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("startLine must be >= 1");
    }

    @Test
    @DisplayName("UTC-S1.1-US01-004 / FTC-S1.1-002: endLine less than startLine throws IllegalArgumentException")
    void shouldThrowWhenEndLineLessThanStartLine() {
        assertThatThrownBy(() -> new SourceRange(50, 1, 10, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endLine cannot be less than startLine");
    }

    @Test
    @DisplayName("UTC-S1.1-US01-005: Same line with endColumn less than startColumn throws IllegalArgumentException")
    void shouldThrowWhenSameLineAndEndColumnLessThanStartColumn() {
        assertThatThrownBy(() -> new SourceRange(5, 20, 5, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endColumn cannot be less than startColumn on the same line");
    }
}
