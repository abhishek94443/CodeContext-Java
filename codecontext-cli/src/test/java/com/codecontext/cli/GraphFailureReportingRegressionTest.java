package com.codecontext.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import java.io.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class GraphFailureReportingRegressionTest {
    @Test void pqcReportsIncompleteGraph(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve("Broken.java"), "class Broken { void f(");
        var err=new StringWriter();
        var cli=new CommandLine(new CryptoPqcCommand()).setErr(new PrintWriter(err)).setOut(new PrintWriter(new StringWriter()));
        cli.execute(repo.toString(),"--format","json");
        assertThat(err.toString()).contains("Graph analysis incomplete");
    }
    @Test void securityHtmlReportsIncompleteGraph(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve("Broken.java"), "class Broken { void f(");
        var err=new StringWriter();
        var cli=new CommandLine(new SecurityScanCommand()).setErr(new PrintWriter(err)).setOut(new PrintWriter(new StringWriter()));
        cli.execute(repo.toString(),"-o",repo.resolve("security.html").toString());
        assertThat(err.toString()).contains("Graph analysis incomplete");
    }
}
