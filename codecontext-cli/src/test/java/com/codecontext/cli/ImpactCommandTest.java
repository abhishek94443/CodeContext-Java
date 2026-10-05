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

class ImpactCommandTest {

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
        String service = """
                package com.example;
                public class OrderService {
                    private PaymentService payment = new PaymentService();
                    public void process() { payment.pay(); }
                }
                """;

        String payment = """
                package com.example;
                public class PaymentService {
                    public void pay() {}
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

        String serviceTest = """
                package com.example;
                public class OrderServiceTest {
                    public void testProcess() {}
                }
                """;

        Files.writeString(pkg.resolve("OrderService.java"), service);
        Files.writeString(pkg.resolve("PaymentService.java"), payment);
        Files.writeString(pkg.resolve("OrderController.java"), controller);
        Files.writeString(pkg.resolve("ApiGateway.java"), gateway);
        Files.writeString(pkg.resolve("OrderServiceTest.java"), serviceTest);
    }

    @Test
    @DisplayName("UTC-S3.2-US01-001: Resolve direct callers when target class is analyzed")
    void should_resolve_direct_callers_when_valid_class_provided() {
        String[] args = new String[]{"impact", "OrderService", "--path", tempRepo.toString()};

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("Blast Radius Analysis: OrderService");
        assertThat(output).contains("Direct Callers (In-degree: 1)");
        assertThat(output).contains("com.example.OrderController");
    }

    @Test
    @DisplayName("UTC-S3.2-US01-002: Transitive tree bounded to specified depth")
    void should_bound_transitive_tree_to_specified_depth() {
        String[] args = new String[]{"impact", "PaymentService", "--path", tempRepo.toString(), "--depth", "2"};

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("Transitive Upstream Tree (Depth: 2)");
        assertThat(output).contains("OrderService");
        assertThat(output).contains("OrderController");
    }

    @Test
    @DisplayName("UTC-S3.2-US01-003: Return exit code 2 when class not found in symbol table")
    void should_return_exit_code_2_when_class_not_found() {
        String[] args = new String[]{"impact", "NonExistentService", "--path", tempRepo.toString()};

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(2);
        assertThat(errCapture.toString()).contains("Class not found in symbol index: NonExistentService");
    }

    @Test
    @DisplayName("UTC-S3.2-US01-004: Suggest matching test classes when flag enabled")
    void should_suggest_matching_test_classes_when_flag_enabled() {
        String[] args = new String[]{"impact", "OrderService", "--path", tempRepo.toString(), "--suggest-tests"};

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(0);
        String output = outCapture.toString();
        assertThat(output).contains("Recommended Test Scope:");
        assertThat(output).contains("OrderServiceTest");
    }

    @Test
    @DisplayName("UTC-S3.2-US01-005: Output valid JSON hierarchy when json flag is passed")
    void should_output_valid_json_hierarchy_when_json_flag_passed() throws Exception {
        String[] args = new String[]{"impact", "OrderService", "--path", tempRepo.toString(), "--json"};

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(0);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(outCapture.toString());
        assertThat(json.has("targetClass")).isTrue();
        assertThat(json.get("targetClass").asText()).isEqualTo("OrderService");
        assertThat(json.get("inDegree").asInt()).isEqualTo(1);
        assertThat(json.has("directCallers")).isTrue();
        assertThat(json.has("transitiveCallers")).isTrue();
    }
}
