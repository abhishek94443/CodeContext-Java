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

class SarifCommandTest {

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

        // Circular Dependency: CycleA -> CycleB -> CycleA
        String classA = """
                package com.example;
                public class CycleA {
                    private CycleB b = new CycleB();
                    public void callB() { b.callA(); }
                }
                """;

        String classB = """
                package com.example;
                public class CycleB {
                    private CycleA a = new CycleA();
                    public void callA() { a.callB(); }
                }
                """;

        Files.writeString(pkg.resolve("CycleA.java"), classA);
        Files.writeString(pkg.resolve("CycleB.java"), classB);
    }

    @Test
    @DisplayName("UTC-S3.2-US02-001 & UTC-S3.2-US02-002 & UTC-S3.2-US02-003: Generate valid OASIS SARIF 2.1.0 with POSIX relative paths")
    void should_generate_valid_sarif_report(@TempDir Path outputDir) throws Exception {
        Path targetSarif = outputDir.resolve("results.sarif");
        String[] args = new String[]{
                "sarif", tempRepo.toString(),
                "--output", targetSarif.toString()
        };

        int exitCode = CodeContextLauncher.execute(args, out, err);

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(targetSarif)).isTrue();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(targetSarif));

        // Schema & Version
        assertThat(root.get("version").asText()).isEqualTo("2.1.0");
        assertThat(root.get("$schema").asText()).contains("sarif-schema-2.1.0.json");

        // Driver & Rules
        JsonNode driver = root.get("runs").get(0).get("tool").get("driver");
        assertThat(driver.get("name").asText()).isEqualTo("CodeContext");

        JsonNode rules = driver.get("rules");
        boolean hasCc001 = false;
        for (JsonNode r : rules) {
            if ("CC001".equals(r.get("id").asText())) hasCc001 = true;
        }
        assertThat(hasCc001).isTrue();

        // Results (Cycle detected)
        JsonNode results = root.get("runs").get(0).get("results");
        assertThat(results.size()).isGreaterThanOrEqualTo(1);

        JsonNode firstResult = results.get(0);
        assertThat(firstResult.get("ruleId").asText()).isEqualTo("CC001");
        assertThat(firstResult.get("level").asText()).isEqualTo("error");
        assertThat(firstResult.get("message").get("text").asText()).contains("Architectural cycle detected");

        // POSIX relative URI check (no backslashes or drive letters)
        JsonNode loc = firstResult.get("locations").get(0).get("physicalLocation").get("artifactLocation");
        String uri = loc.get("uri").asText();
        assertThat(uri).doesNotContain("\\");
        assertThat(uri).doesNotContain(":");
        assertThat(uri).contains("CycleA.java");
    }
}
