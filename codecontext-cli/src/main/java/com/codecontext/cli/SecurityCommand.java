package com.codecontext.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Top-level CLI command group: codecontext security [SUBCOMMAND]
 */
@Command(
        name = "security",
        description = "Cryptographic security analysis and post-quantum migration assessment",
        subcommands = {
                SecurityScanCommand.class
        }
)
public class SecurityCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}

