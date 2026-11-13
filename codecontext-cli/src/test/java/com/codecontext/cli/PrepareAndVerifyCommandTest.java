package com.codecontext.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrepareAndVerifyCommandTest {

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

        String payment = """
                package com.example;
                public class PaymentService {
                    public void pay() {}
                }
                """;

        Files.writeString(pkg.resolve("OrderService.java"), service);
        Files.writeString(pkg.resolve("PaymentService.java"), payment);

        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();
    }

    @Test
    @DisplayName("UTC-S3.3-US03-001: should synthesize advisory EARS plan on prepare")
    void shouldSynthesizePlanOnPrepare() throws Exception {
        CommandLine cmd = new CommandLine(new PrepareCommand());
        cmd.setOut(out);
        cmd.setErr(err);

        Path planFile = tempRepo.resolve(".codecontext-plan.json");
        int exitCode = cmd.execute("--target", "OrderService", "--path", tempRepo.toString(), "--output", planFile.toString());

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(planFile)).isTrue();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode plan = mapper.readTree(planFile.toFile());
        assertThat(plan.get("targetClass").asText()).isEqualTo("com.example.OrderService");
        assertThat(plan.get("allowedModifiedFiles").toString()).contains("OrderService.java");
        assertThat(plan.get("earsRules").toString()).contains("THE SYSTEM SHALL NOT break public API signatures");
    }

    @Test
    @DisplayName("UTC-S3.3-US03-002: should pass verify when modifications are within authorized plan")
    void shouldPassVerifyWhenAllowed() throws Exception {
        // 1. Prepare plan for OrderService
        CommandLine prepCmd = new CommandLine(new PrepareCommand());
        Path planFile = tempRepo.resolve(".codecontext-plan.json");
        prepCmd.execute("--target", "OrderService", "--path", tempRepo.toString(), "--output", planFile.toString());

        // 2. Modify OrderService
        Files.writeString(tempRepo.resolve("com/example/OrderService.java"), """
                package com.example;
                public class OrderService {
                    public void process() { int mod = 1; }
                }
                """);

        // 3. Verify
        CommandLine verifyCmd = new CommandLine(new VerifyCommand());
        verifyCmd.setOut(out);
        verifyCmd.setErr(err);

        int exitCode = verifyCmd.execute("--plan", planFile.toString(), "--path", tempRepo.toString());

        assertThat(exitCode).isEqualTo(0);
        assertThat(outCapture.toString()).contains("Verification passed");
    }

    @Test
    @DisplayName("UTC-S3.3-US03-003: should fail verify with exit code 1 when unauthorized file is modified")
    void shouldFailVerifyOnScopeViolation() throws Exception {
        // 1. Prepare plan for OrderService (only OrderService allowed)
        CommandLine prepCmd = new CommandLine(new PrepareCommand());
        Path planFile = tempRepo.resolve(".codecontext-plan.json");
        prepCmd.execute("--target", "OrderService", "--path", tempRepo.toString(), "--output", planFile.toString());

        // 2. Modify PaymentService (outside authorized scope!)
        Files.writeString(tempRepo.resolve("com/example/PaymentService.java"), """
                package com.example;
                public class PaymentService {
                    public void pay() { int unauthorized = 99; }
                }
                """);

        // 3. Verify
        CommandLine verifyCmd = new CommandLine(new VerifyCommand());
        verifyCmd.setOut(out);
        verifyCmd.setErr(err);

        int exitCode = verifyCmd.execute("--plan", planFile.toString(), "--path", tempRepo.toString());

        assertThat(exitCode).isEqualTo(CliExitCode.VIOLATION.getCode());
        assertThat(errCapture.toString()).contains("Scope violation");
        assertThat(errCapture.toString()).contains("PaymentService.java");
    }
}
