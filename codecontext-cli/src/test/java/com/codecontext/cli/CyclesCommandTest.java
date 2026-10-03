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

class CyclesCommandTest {

    private final StringWriter outCapture = new StringWriter();
    private final StringWriter errCapture = new StringWriter();
    private final PrintWriter out = new PrintWriter(outCapture);
    private final PrintWriter err = new PrintWriter(errCapture);

    @Test
    @DisplayName("FTC-S3.1-010 & UTC-S3.1-US03-001: cycles --fail-on-cycles exits 0 on clean DAG")
    void should_exit_zero_on_dag(@TempDir Path dagRepo) throws IOException {
        Path pkg = dagRepo.resolve("com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("Controller.java"), """
                package com.example;
                public class Controller {
                    private Service s = new Service();
                    public void handle() { s.serve(); }
                }
                """);

        Files.writeString(pkg.resolve("Service.java"), """
                package com.example;
                public class Service {
                    public void serve() {}
                }
                """);

        String[] args = new String[]{"cycles", dagRepo.toString(), "--fail-on-cycles"};
        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(outCapture.toString()).contains("0 circular dependencies detected");
    }

    @Test
    @DisplayName("FTC-S3.1-008 & FTC-S3.1-009 & UTC-S3.1-US03-002: cycles --fail-on-cycles blocks CI with exit code 1")
    void should_fail_ci_gate_on_cyclic_repo(@TempDir Path cyclicRepo) throws IOException {
        Path pkg = cyclicRepo.resolve("com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("ClassA.java"), """
                package com.example;
                public class ClassA {
                    private ClassB b = new ClassB();
                    public void callB() { b.callA(); }
                }
                """);

        Files.writeString(pkg.resolve("ClassB.java"), """
                package com.example;
                public class ClassB {
                    private ClassA a = new ClassA();
                    public void callA() { a.callB(); }
                }
                """);

        // 1. Without --fail-on-cycles -> exit 0
        String[] argsReportOnly = new String[]{"cycles", cyclicRepo.toString()};
        int exitReport = CodeContextLauncher.execute(argsReportOnly, out, err);
        assertThat(exitReport).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(outCapture.toString()).contains("circular dependency loop");

        // 2. With --fail-on-cycles -> exit 1 (VIOLATION)
        StringWriter errFail = new StringWriter();
        PrintWriter errFailPw = new PrintWriter(errFail);
        String[] argsFail = new String[]{"cycles", cyclicRepo.toString(), "--fail-on-cycles"};
        int exitFail = CodeContextLauncher.execute(argsFail, out, errFailPw);
        assertThat(exitFail).isEqualTo(CliExitCode.VIOLATION.getCode());
        assertThat(errFail.toString()).contains("CI Gate Violation");
    }

    @Test
    @DisplayName("FTC-S3.1-011 & UTC-S3.1-US03-005: cycles --json outputs valid JSON array of cycles")
    void should_output_json_cycle_report(@TempDir Path cyclicRepo) throws Exception {
        Path pkg = cyclicRepo.resolve("com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("ClassA.java"), """
                package com.example;
                public class ClassA {
                    private ClassB b = new ClassB();
                    public void callB() { b.callA(); }
                }
                """);

        Files.writeString(pkg.resolve("ClassB.java"), """
                package com.example;
                public class ClassB {
                    private ClassA a = new ClassA();
                    public void callA() { a.callB(); }
                }
                """);

        String[] args = new String[]{"cycles", cyclicRepo.toString(), "--json"};
        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        String output = outCapture.toString().trim();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(output);
        assertThat(root.has("totalCycles")).isTrue();
        assertThat(root.get("totalCycles").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(root.has("cycles")).isTrue();
    }
    @Test
    @DisplayName("FTC-S3.1-BUG02-001: [BUG-S3.1-02] cycles does not treat single-node self-loop recursion as architectural cycle")
    void should_not_fail_on_self_loop_recursion(@TempDir Path repo) throws IOException {
        Path pkg = repo.resolve("com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("TreeWalker.java"), """
                package com.example;
                public class TreeWalker {
                    public void walk() { walk(); }
                }
                """);

        String[] args = new String[]{"cycles", repo.toString(), "--fail-on-cycles"};
        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(CliExitCode.SUCCESS.getCode());
        assertThat(outCapture.toString()).contains("0 circular dependencies detected");
    }
}