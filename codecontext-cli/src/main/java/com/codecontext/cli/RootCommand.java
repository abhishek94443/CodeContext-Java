package com.codecontext.cli;

import picocli.CommandLine.Command;

@Command(
        name = "codecontext",
        description = "CodeContext: High-performance architecture intelligence and change-safety engine",
        version = "CodeContext version 2.0.0 (Java 21)",
        mixinStandardHelpOptions = true,
        subcommands = {
                AnalyzeCommand.class,
                CyclesCommand.class,
                ImpactCommand.class,
                SarifCommand.class
        }
)
public class RootCommand implements Runnable {
    @Override
    public void run() {
        // Invoked when no subcommand is specified
    }
}
