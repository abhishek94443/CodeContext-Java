package com.codecontext.cli;

public enum CliExitCode {
    SUCCESS(0),
    VIOLATION(1),
    BAD_ARGS(2),
    FATAL_ERROR(3);

    private final int code;

    CliExitCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
