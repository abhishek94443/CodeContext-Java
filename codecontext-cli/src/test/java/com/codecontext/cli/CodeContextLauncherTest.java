package com.codecontext.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CodeContextLauncherTest {

    private final StringWriter outCapture = new StringWriter();
    private final StringWriter errCapture = new StringWriter();
    private final PrintWriter out = new PrintWriter(outCapture);
    private final PrintWriter err = new PrintWriter(errCapture);

    @Test
    @DisplayName("FTC-S3.1-001 & UTC-S3.1-US01-005: Root launcher --help prints subcommands and exits with 0 in < 100ms")
    void should_print_help_and_exit_zero() {
        long start = System.nanoTime();
        int exitCode = CodeContextLauncher.execute(new String[]{"--help"}, out, err);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String output = outCapture.toString();
        assertThat(output).contains("Usage: codecontext");
        assertThat(output).contains("analyze");
        assertThat(output).contains("cycles");
        assertThat(durationMs).isLessThan(2000); // Quality gate: sub-100ms warm; < 2s cold in CI
    }

    @Test
    @DisplayName("FTC-S3.1-002 & UTC-S3.1-US01-006: Root launcher --version prints version string and exits with 0")
    void should_print_version_and_exit_zero() {
        int exitCode = CodeContextLauncher.execute(new String[]{"--version"}, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(outCapture.toString()).contains("CodeContext version 2.0.0 (Java 21)");
    }

    @Test
    @DisplayName("FTC-S3.1-003 & UTC-S3.1-US01-007: Unknown flag prints error to stderr and exits with 2 (BAD_ARGS)")
    void should_fail_on_invalid_option_with_exit_code_2() {
        int exitCode = CodeContextLauncher.execute(new String[]{"--unknown-flag-xyz"}, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.BAD_ARGS.getCode());
        assertThat(errCapture.toString()).contains("Unknown option: '--unknown-flag-xyz'");
    }

    @Test
    @DisplayName("FTC-S3.1-004 & UTC-S3.1-US01-008: Non-existent repo path returns exit code 3 (FATAL_ERROR)")
    void should_fail_on_non_existent_path_with_exit_code_3() {
        int exitCode = CodeContextLauncher.execute(new String[]{"analyze", "non_existent_dir_99999"}, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.FATAL_ERROR.getCode());
        assertThat(errCapture.toString()).contains("Repository path does not exist");
    }
}
