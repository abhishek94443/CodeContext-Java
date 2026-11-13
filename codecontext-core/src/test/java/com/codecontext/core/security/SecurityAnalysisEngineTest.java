package com.codecontext.core.security;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.model.ParsedCompilationUnit;
import com.codecontext.core.parser.Pass2InvocationResolver;
import com.codecontext.core.resolver.ResolutionChain;
import com.codecontext.core.security.rule.DemonstrationSecurityRule;
import com.codecontext.core.security.rule.SecurityRuleRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAnalysisEngineTest {

    @Test
    @DisplayName("SecurityAnalysisEngine detects AST method call with exact source coordinates using demonstration rule")
    void testDemonstrationRuleDetection(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        String code = """
                package com.example;
                public class DemoTarget {
                    public void execute() {
                        int x = 42;
                        codecontextDemoHook();
                    }
                    private void codecontextDemoHook() {}
                }
                """;
        Path targetFile = pkg.resolve("DemoTarget.java");
        Files.writeString(targetFile, code);

        SecurityRuleRegistry registry = new SecurityRuleRegistry();
        registry.register(new DemonstrationSecurityRule());

        SecurityAnalysisEngine engine = new SecurityAnalysisEngine(registry);
        SecurityAnalysisResult result = engine.analyze(tempRepo, null, false);

        assertThat(result.filesScanned()).isEqualTo(1);
        assertThat(result.failedFiles()).isEmpty();
        assertThat(result.hasFindings()).isTrue();
        assertThat(result.findings()).hasSize(1);

        Finding finding = result.findings().get(0);
        assertThat(finding.ruleId()).isEqualTo(DemonstrationSecurityRule.RULE_ID);
        assertThat(finding.category()).isEqualTo(FindingCategory.CODE_QUALITY);
        assertThat(finding.filePath()).contains(targetFile);

        // Verify exact coordinates: codecontextDemoHook() is on line 5
        assertThat(finding.location()).isPresent();
        assertThat(finding.location().get().startLine()).isEqualTo(5);
        assertThat(finding.location().get().endLine()).isEqualTo(5);
    }

    @Test
    @DisplayName("SecurityAnalysisEngine returns empty result cleanly when no rules are registered")
    void testNoRulesEmptyResult(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Sample.java"), "package com.example; public class Sample {}");

        SecurityRuleRegistry emptyRegistry = SecurityRuleRegistry.empty();
        SecurityAnalysisEngine engine = new SecurityAnalysisEngine(emptyRegistry);

        SecurityAnalysisResult result = engine.analyze(tempRepo, null, false);

        assertThat(result.hasFindings()).isFalse();
        assertThat(result.totalFindings()).isEqualTo(0);
        assertThat(result.findings()).isEmpty();
    }

    @Test
    @DisplayName("SecurityAnalysisEngine handles syntax/parse errors gracefully without throwing exceptions")
    void testParseErrorHandling(@TempDir Path tempRepo) throws IOException {
        Path pkg = tempRepo.resolve("com/example");
        Files.createDirectories(pkg);

        // Corrupted Java file
        Path corruptFile = pkg.resolve("Corrupt.java");
        Files.writeString(corruptFile, "class Corrupt { incomplete method without syntax }");

        SecurityRuleRegistry registry = new SecurityRuleRegistry();
        registry.register(new DemonstrationSecurityRule());

        SecurityAnalysisEngine engine = new SecurityAnalysisEngine(registry);
        SecurityAnalysisResult result = engine.analyze(tempRepo, null, false);

        assertThat(result.failedFiles()).contains(corruptFile);
    }

    @Test
    @DisplayName("Pass2InvocationResolver integrates with SecurityAnalysisEngine without redundant parsing")
    void testPass2InvocationResolverIntegration(@TempDir Path tempRepo) throws IOException {
        String code = """
                package com.example;
                public class CombinedService {
                    public void run() {
                        codecontextDemoHook();
                    }
                    private void codecontextDemoHook() {}
                }
                """;
        Path path = tempRepo.resolve("CombinedService.java");
        Files.writeString(path, code);

        ConcurrentSymbolTable symbolTable = new ConcurrentSymbolTable();
        TypeHierarchyIndex hierarchyIndex = new TypeHierarchyIndex(symbolTable);
        ResolutionChain resolutionChain = ResolutionChain.standardChain();

        Pass2InvocationResolver resolver = new Pass2InvocationResolver(symbolTable, hierarchyIndex, resolutionChain);

        SecurityRuleRegistry registry = new SecurityRuleRegistry();
        registry.register(new DemonstrationSecurityRule());
        SecurityAnalysisEngine engine = new SecurityAnalysisEngine(registry);

        List<Finding> findings = new ArrayList<>();
        ParsedCompilationUnit unit = resolver.resolve(path, code, engine, findings);

        assertThat(unit).isNotNull();
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo(DemonstrationSecurityRule.RULE_ID);
        assertThat(findings.get(0).location().get().startLine()).isEqualTo(4);
    }
}

