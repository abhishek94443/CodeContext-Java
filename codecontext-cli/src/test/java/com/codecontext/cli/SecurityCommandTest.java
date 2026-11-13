package com.codecontext.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCommandTest {

    private final StringWriter outCapture = new StringWriter();
    private final StringWriter errCapture = new StringWriter();
    private final PrintWriter out = new PrintWriter(outCapture);
    private final PrintWriter err = new PrintWriter(errCapture);

    @Test
    @DisplayName("codecontext security displays usage and exits 0 when no subcommand provided")
    void testSecurityCommandUsage() {
        int exitCode = CodeContextLauncher.execute(new String[]{"security"}, out, err);
        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(outCapture.toString()).contains("Commands:");
        assertThat(outCapture.toString()).contains("scan");
    }

    @Test
    @DisplayName("codecontext security scan exits 0 with clean status when no rules or findings exist")
    void testSecurityScanCleanScenario(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("CleanService.java"), "package com.example; public class CleanService {}");

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString()
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();
        assertThat(stdout).contains("CodeContext Security & Cryptographic Analysis");
        assertThat(stdout).contains("Status: Analysis complete. 0 findings detected");
    }

    @Test
    @DisplayName("codecontext security scan --demo-rule detects finding and displays terminal table")
    void testSecurityScanWithDemoRule(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                public class DemoService {
                    public void doWork() {
                        codecontextDemoHook();
                    }
                    private void codecontextDemoHook() {}
                }
                """;
        Files.writeString(pkg.resolve("DemoService.java"), code);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(),
                "--demo-rule"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String stdout = outCapture.toString();
        assertThat(stdout).contains("Detected 1 Security Finding");
        assertThat(stdout).contains("DEMO001");
        assertThat(stdout).contains("DemoService.java:4");
    }

    @Test
    @DisplayName("codecontext security scan --demo-rule --fail-on-findings exits with code 1")
    void testSecurityScanFailOnFindings(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                public class DemoService {
                    public void run() { codecontextDemoHook(); }
                    private void codecontextDemoHook() {}
                }
                """;
        Files.writeString(pkg.resolve("DemoService.java"), code);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(),
                "--demo-rule",
                "--fail-on-findings"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.VIOLATION.getCode());
        assertThat(errCapture.toString()).contains("Security Quality Gate Violation");
    }

    @Test
    @DisplayName("codecontext security scan --demo-rule --format json emits valid JSON array")
    void testSecurityScanJsonOutput(@TempDir Path tempRepo) throws Exception {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                public class DemoService {
                    public void run() { codecontextDemoHook(); }
                    private void codecontextDemoHook() {}
                }
                """;
        Files.writeString(pkg.resolve("DemoService.java"), code);

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(),
                "--demo-rule",
                "--format", "json"
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode array = mapper.readTree(outCapture.toString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(1);
        assertThat(array.get(0).get("ruleId").asText()).isEqualTo("DEMO001");
    }

    @Test
    @DisplayName("codecontext security scan --demo-rule --format sarif exports valid SARIF 2.1.0 file")
    void testSecurityScanSarifOutput(@TempDir Path tempRepo) throws Exception {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                public class DemoService {
                    public void run() { codecontextDemoHook(); }
                    private void codecontextDemoHook() {}
                }
                """;
        Files.writeString(pkg.resolve("DemoService.java"), code);

        Path sarifFile = tempRepo.resolve("security-scan.sarif");

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toString(),
                "--demo-rule",
                "--format", "sarif",
                "-o", sarifFile.toString()
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(Files.exists(sarifFile)).isTrue();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(sarifFile));

        assertThat(root.get("version").asText()).isEqualTo("2.1.0");
        JsonNode rules = root.get("runs").get(0).get("tool").get("driver").get("rules");
        assertThat(rules.size()).isEqualTo(1);
        assertThat(rules.get(0).get("id").asText()).isEqualTo("DEMO001");

        JsonNode results = root.get("runs").get(0).get("results");
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("ruleId").asText()).isEqualTo("DEMO001");
        assertThat(results.get(0).get("locations").get(0).get("physicalLocation").get("region").get("startLine").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("codecontext security scan with non-existent path traps BAD_ARGS (exit code 2)")
    void testSecurityScanNonExistentPath(@TempDir Path tempRepo) {
        Path fake = tempRepo.resolve("does-not-exist");

        int exitCode = CodeContextLauncher.execute(new String[]{
                "security", "scan", fake.toString()
        }, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.BAD_ARGS.getCode());
        assertThat(errCapture.toString()).contains("does not exist");
    }

    @Test
    @DisplayName("codecontext security scan resolves absolute and nested relative paths correctly")
    void testSecurityScanPathResolution(@TempDir Path tempRepo) throws IOException {
        Path sub = tempRepo.resolve("nested/subproject");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("Service.java"), "package sub; public class Service {}");

        // 1. Absolute path
        int exitCode1 = CodeContextLauncher.execute(new String[]{
                "security", "scan", tempRepo.toAbsolutePath().toString(), "--no-color"
        }, out, err);
        assertThat(exitCode1).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(outCapture.toString()).contains("Repository Path : " + tempRepo.toAbsolutePath().normalize());

        // 2. Subdirectory path
        outCapture.getBuffer().setLength(0);
        int exitCode2 = CodeContextLauncher.execute(new String[]{
                "security", "scan", sub.toAbsolutePath().toString(), "--no-color"
        }, out, err);
        assertThat(exitCode2).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(outCapture.toString()).contains("Repository Path : " + sub.toAbsolutePath().normalize());
    }
}

