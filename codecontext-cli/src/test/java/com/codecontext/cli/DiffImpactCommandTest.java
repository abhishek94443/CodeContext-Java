package com.codecontext.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiffImpactCommandTest {

    private final StringWriter outCapture = new StringWriter();
    private final StringWriter errCapture = new StringWriter();
    private final PrintWriter out = new PrintWriter(outCapture);
    private final PrintWriter err = new PrintWriter(errCapture);

    @TempDir
    Path tempRepo;

    private Git git;

    @BeforeEach
    void setupRepository() throws Exception {
        git = Git.init().setDirectory(tempRepo.toFile()).call();

        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String service = """
                package com.example;
                public class OrderService {
                    public void process() {}
                }
                """;

        String controller = """
                package com.example;
                public class OrderController {
                    private OrderService service = new OrderService();
                    public void handle() { service.process(); }
                }
                """;

        String test = """
                package com.example;
                public class OrderServiceTest {
                    public void testProcess() {}
                }
                """;

        Files.writeString(pkg.resolve("OrderService.java"), service);
        Files.writeString(pkg.resolve("OrderController.java"), controller);
        Files.writeString(pkg.resolve("OrderServiceTest.java"), test);

        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();
    }

    private CommandLine createCommandLine() {
        CommandLine cmd = new CommandLine(new DiffImpactCommand());
        cmd.setOut(out);
        cmd.setErr(err);
        return cmd;
    }

    @Test
    @DisplayName("UTC-S3.3-US01-001: should analyze working tree diff and output markdown PR summary")
    void shouldAnalyzeWorkingTreeDiff() throws Exception {
        // Modify OrderService
        Path serviceFile = tempRepo.resolve("com/example/OrderService.java");
        Files.writeString(serviceFile, """
                package com.example;
                public class OrderService {
                    public void process() { int updated = 1; }
                }
                """);

        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("--path", tempRepo.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("CodeContext Pull Request Blast Radius & Architecture Check");
        assertThat(output).contains("com.example.OrderService");
        assertThat(output).contains("Direct Callers (In-degree: 1)");
        assertThat(output).contains("Passed (Clean)");
        assertThat(output).contains("com.example.OrderServiceTest");
    }

    @Test
    @DisplayName("UTC-S3.3-US01-002: should fail with exit code 1 when diff introduces architectural cycle")
    void shouldFailOnCycleRegression() throws Exception {
        // Introduce cycle: OrderService now calls OrderController
        Path serviceFile = tempRepo.resolve("com/example/OrderService.java");
        Files.writeString(serviceFile, """
                package com.example;
                public class OrderService {
                    private OrderController controller = new OrderController();
                    public void process() { controller.handle(); }
                }
                """);

        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("--path", tempRepo.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(CliExitCode.VIOLATION.getCode());
        String output = outCapture.toString();
        assertThat(output).contains("Blocked (Cycles Detected)");
        String errOutput = errCapture.toString();
        assertThat(errOutput).contains("Architecture violation");
    }

    @Test
    @DisplayName("UTC-S3.3-US01-003: should write markdown report to file when --output specified")
    void shouldWriteReportToFile() throws Exception {
        Path serviceFile = tempRepo.resolve("com/example/OrderService.java");
        Files.writeString(serviceFile, """
                package com.example;
                public class OrderService {
                    public void process() { int mod = 2; }
                }
                """);

        Path reportFile = tempRepo.resolve("pr-summary.md");
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("--path", tempRepo.toString(), "-o", reportFile.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(reportFile)).isTrue();
        String fileContent = Files.readString(reportFile);
        assertThat(fileContent).contains("CodeContext Pull Request Blast Radius");
    }

    @Test
    @DisplayName("UTC-S3.3-US01-004: should handle clean diff gracefully")
    void shouldHandleCleanDiff() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("--path", tempRepo.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("No modified Java files detected");
    }
}
