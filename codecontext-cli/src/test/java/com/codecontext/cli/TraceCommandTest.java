package com.codecontext.cli;

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

class TraceCommandTest {

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

        // Chain: ApiGateway -> OrderController -> OrderService -> PaymentService
        // Isolated: HealthCheck
        String payment = """
                package com.example;
                public class PaymentService {
                    public void pay() {}
                }
                """;

        String service = """
                package com.example;
                public class OrderService {
                    private PaymentService payment = new PaymentService();
                    public void process() { payment.pay(); }
                }
                """;

        String controller = """
                package com.example;
                public class OrderController {
                    private OrderService service = new OrderService();
                    public void handle() { service.process(); }
                }
                """;

        String gateway = """
                package com.example;
                public class ApiGateway {
                    private OrderController controller = new OrderController();
                    public void route() { controller.handle(); }
                }
                """;

        String health = """
                package com.example;
                public class HealthCheck {
                    public void ping() {}
                }
                """;

        Files.writeString(pkg.resolve("PaymentService.java"), payment);
        Files.writeString(pkg.resolve("OrderService.java"), service);
        Files.writeString(pkg.resolve("OrderController.java"), controller);
        Files.writeString(pkg.resolve("ApiGateway.java"), gateway);
        Files.writeString(pkg.resolve("HealthCheck.java"), health);
    }

    private CommandLine createCommandLine() {
        CommandLine cmd = new CommandLine(new TraceCommand());
        cmd.setOut(out);
        cmd.setErr(err);
        return cmd;
    }

    @Test
    @DisplayName("UTC-S3.3-US02-001: should trace direct path between adjacent classes")
    void shouldTraceDirectPath() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("OrderController", "OrderService", "--path", tempRepo.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("Execution Path Trace: OrderController -> OrderService (1 hop)");
        assertThat(output).contains("Step 1: com.example.OrderController");
        assertThat(output).contains("calls OrderService.process()");
    }

    @Test
    @DisplayName("UTC-S3.3-US02-002: should trace multi-hop path across component chain")
    void shouldTraceMultiHopPath() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("ApiGateway", "PaymentService", "--path", tempRepo.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("Execution Path Trace: ApiGateway -> PaymentService (3 hops)");
        assertThat(output).contains("Step 1: com.example.ApiGateway");
        assertThat(output).contains("Step 2: com.example.OrderController");
        assertThat(output).contains("Step 3: com.example.OrderService");
        assertThat(output).contains("Final Target: com.example.PaymentService");
    }

    @Test
    @DisplayName("UTC-S3.3-US02-003: should report no path when classes are disconnected")
    void shouldReportNoPathWhenDisconnected() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("ApiGateway", "HealthCheck", "--path", tempRepo.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("No execution path exists from ApiGateway to HealthCheck");
    }

    @Test
    @DisplayName("UTC-S3.3-US02-004: should return exit code 2 when class not found")
    void shouldReturnExitCode2WhenClassNotFound() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("ApiGateway", "NonExistentService", "--path", tempRepo.toString(), "--no-color");

        assertThat(exitCode).isEqualTo(CliExitCode.BAD_ARGS.getCode());
        String errOutput = errCapture.toString();
        assertThat(errOutput).contains("Class not found");
    }

    @Test
    @DisplayName("UTC-S3.3-US02-005: should report path exceeds max hops cap")
    void shouldReportMaxHopsCap() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("ApiGateway", "PaymentService", "--path", tempRepo.toString(), "--max-hops", "1", "--no-color");

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("exceeds maximum allowed hops");
    }
}
