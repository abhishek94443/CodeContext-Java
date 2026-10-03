package com.codecontext.cli;

import picocli.CommandLine;

import java.io.PrintWriter;

public class CodeContextLauncher {

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        int exitCode = execute(args, out, err);
        System.exit(exitCode);
    }

    public static int execute(String[] args, PrintWriter out, PrintWriter err) {
        CommandLine cmd = new CommandLine(new RootCommand());
        cmd.setOut(out);
        cmd.setErr(err);

        // Trap argument syntax errors to BAD_ARGS (2)
        cmd.setParameterExceptionHandler((ex, cmdArgs) -> {
            err.println(ex.getMessage());
            ex.getCommandLine().usage(err);
            return CliExitCode.BAD_ARGS.getCode();
        });

        // Trap execution exceptions to FATAL_ERROR (3)
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            err.println("Fatal Error: " + ex.getMessage());
            return CliExitCode.FATAL_ERROR.getCode();
        });

        // If no args provided, show usage and exit 0
        if (args == null || args.length == 0) {
            cmd.usage(out);
            return CliExitCode.SUCCESS.getCode();
        }

        return cmd.execute(args);
    }
}
