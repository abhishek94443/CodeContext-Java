package com.codecontext.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CliExitCodeTest {

    @Test
    @DisplayName("UTC-S3.1-US01-001: SUCCESS maps to exit code 0")
    void should_map_success_to_zero() {
        assertThat(CliExitCode.SUCCESS.getCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("UTC-S3.1-US01-002: VIOLATION maps to exit code 1")
    void should_map_violation_to_one() {
        assertThat(CliExitCode.VIOLATION.getCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTC-S3.1-US01-003: BAD_ARGS maps to exit code 2")
    void should_map_bad_args_to_two() {
        assertThat(CliExitCode.BAD_ARGS.getCode()).isEqualTo(2);
    }

    @Test
    @DisplayName("UTC-S3.1-US01-004: FATAL_ERROR maps to exit code 3")
    void should_map_fatal_error_to_three() {
        assertThat(CliExitCode.FATAL_ERROR.getCode()).isEqualTo(3);
    }
}
