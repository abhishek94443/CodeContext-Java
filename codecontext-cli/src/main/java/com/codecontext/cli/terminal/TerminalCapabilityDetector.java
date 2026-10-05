package com.codecontext.cli.terminal;

/**
 * Utility for detecting terminal capabilities and handling ANSI color stripping.
 */
public class TerminalCapabilityDetector {

    public static boolean isAnsiSupported(boolean noColorFlag) {
        if (noColorFlag) {
            return false;
        }
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        String term = System.getenv("TERM");
        return term == null || !term.equalsIgnoreCase("dumb");
    }

    public static String stripAnsi(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\u001B\\[[;?0-9]*[a-zA-Z]", "");
    }
}
