package com.codecontext.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeCommandTest {

    private final StringWriter outCapture = new StringWriter();
    private final StringWriter errCapture = new StringWriter();
    private final PrintWriter out = new PrintWriter(outCapture);
    private final PrintWriter err = new PrintWriter(errCapture);

    @TempDir
    Path tempRepo;

    @BeforeEach
    void setupRepository() throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String classA = """
                package com.example;
                public class ServiceA {
                    private ServiceB b = new ServiceB();
                    public void doA() { b.doB(); }
                }
                """;

        String classB = """
                package com.example;
                public class ServiceB {
                    public void doB() {}
                }
                """;

        Files.writeString(pkg.resolve("ServiceA.java"), classA);
        Files.writeString(pkg.resolve("ServiceB.java"), classB);
    }

    @Test
    @DisplayName("FTC-S3.1-005 & UTC-S3.1-US02-001 & UTC-S3.1-US02-004: analyze scans repo, renders table, and exports HTML")
    void should_analyze_and_generate_html_report(@TempDir Path outputDir) {
        String[] args = new String[]{
                "analyze", tempRepo.toString(),
                "--output", outputDir.toString()
        };

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String output = outCapture.toString();
        assertThat(output).contains("CODECONTEXT ARCHITECTURE INTELLIGENCE");
        assertThat(output).contains("ServiceB");
        assertThat(output).contains("ServiceA");

        Path reportPath = outputDir.resolve("codecontext-graph.html");
        assertThat(Files.exists(reportPath)).isTrue();
    }

    @Test
    @DisplayName("FTC-S3.1-006 & UTC-S3.1-US02-003: analyze --json emits clean machine-readable JSON")
    void should_output_clean_json_on_flag() throws Exception {
        String[] args = new String[]{
                "analyze", tempRepo.toString(),
                "--json"
        };

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String output = outCapture.toString().trim();

        // Must not contain ANSI escape sequences
        assertThat(output).doesNotContain("\u001B");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(output);
        assertThat(root.has("totalClasses")).isTrue();
        assertThat(root.get("totalClasses").asInt()).isEqualTo(2);
        assertThat(root.has("hotspots")).isTrue();
    }

    @Test
    @DisplayName("FTC-S3.1-007: analyze -q suppresses table and only outputs report path")
    void should_suppress_table_in_quiet_mode() {
        String[] args = new String[]{
                "analyze", tempRepo.toString(),
                "-q"
        };

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String output = outCapture.toString().trim();
        assertThat(output).doesNotContain("CODECONTEXT ARCHITECTURE INTELLIGENCE");
        assertThat(output).contains("codecontext-graph.html");
    }
    @Test
    @DisplayName("FTC-S3.1-CR01-001: [CR-S3.1-01] analyze excludes test classes by default and includes them when --include-tests is specified")
    void should_exclude_tests_by_default_and_include_with_flag() throws Exception {
        Path pkg = tempRepo.resolve("com/example");
        String testClass = """
                package com.example;
                public class ServiceATest {
                    public void testDoA() {}
                }
                """;
        Files.writeString(pkg.resolve("ServiceATest.java"), testClass);

        // Run default analyze --json
        String[] argsDefault = new String[]{"analyze", tempRepo.toString(), "--json"};
        StringWriter out1 = new StringWriter();
        int exit1 = CodeContextLauncher.execute(argsDefault, new PrintWriter(out1), new PrintWriter(new StringWriter()));
        assertThat(exit1).isEqualTo(CliExitCode.SUCCESS.getCode());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root1 = mapper.readTree(out1.toString().trim());
        assertThat(root1.get("totalFiles").asInt()).isEqualTo(2); // Only ServiceA and ServiceB
        assertThat(root1.get("totalClasses").asInt()).isEqualTo(2);

        // Run with --include-tests
        String[] argsWithTests = new String[]{"analyze", tempRepo.toString(), "--json", "--include-tests"};
        StringWriter out2 = new StringWriter();
        int exit2 = CodeContextLauncher.execute(argsWithTests, new PrintWriter(out2), new PrintWriter(new StringWriter()));
        assertThat(exit2).isEqualTo(CliExitCode.SUCCESS.getCode());

        JsonNode root2 = mapper.readTree(out2.toString().trim());
        assertThat(root2.get("totalFiles").asInt()).isEqualTo(3); // Includes ServiceATest
        assertThat(root2.get("totalClasses").asInt()).isEqualTo(3);
    }
}