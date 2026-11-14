package com.codecontext.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Top-level CLI command group: codecontext crypto [SUBCOMMAND]
 */
@Command(
        name = "crypto",
        description = "Cryptographic asset inventory, CycloneDX 1.6 CBOM, and PQC migration assessment",
        subcommands = {
                CryptoInventoryCommand.class,
                CryptoCbomCommand.class,
                CryptoPqcCommand.class
        }
)
public class CryptoCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}

