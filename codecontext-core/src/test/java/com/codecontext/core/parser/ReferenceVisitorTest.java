package com.codecontext.core.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceVisitorTest {

    private JavaParser parser;
    private ReferenceVisitor visitor;

    @BeforeEach
    void setUp() {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        parser = new JavaParser(config);
        visitor = new ReferenceVisitor();
    }

    private CompilationUnit parse(String code) {
        return parser.parse(code).getResult().orElseThrow();
    }

    @Test
    @DisplayName("UTC-S1.2-US02-019 & FTC-S1.2-008: Method parameters mapped in root method scope")
    void testMethodParametersMapped() {
        String code = """
                package com.example;
                public class TransferService {
                    public void transfer(Account from, Account to, BigDecimal amount) {
                        from.debit(amount);
                    }
                }
                """;

        CompilationUnit cu = parse(code);
        cu.accept(visitor, null);

        assertThat(visitor.getVariableTypeAtMethod("transfer", "from")).contains("Account");
        assertThat(visitor.getVariableTypeAtMethod("transfer", "to")).contains("Account");
        assertThat(visitor.getVariableTypeAtMethod("transfer", "amount")).contains("BigDecimal");
    }

    @Test
    @DisplayName("UTC-S1.2-US02-020 & FTC-S1.2-009: Local variable declaration mapped in block scope")
    void testLocalVariableMapped() {
        String code = """
                package com.example;
                public class OrderService {
                    public void process() {
                        Order order = new Order();
                        order.submit();
                    }
                }
                """;

        CompilationUnit cu = parse(code);
        cu.accept(visitor, null);

        assertThat(visitor.getVariableTypeAtMethod("process", "order")).contains("Order");
    }

    @Test
    @DisplayName("UTC-S1.2-US02-021 & FTC-S1.2-009: Scope cleaned up on block exit")
    void testScopeCleanedUpOnBlockExit() {
        String code = """
                package com.example;
                public class ScopeTest {
                    public void testScope() {
                        int outer = 1;
                        if (true) {
                            String innerMsg = "hello";
                        }
                        outer++;
                    }
                }
                """;

        CompilationUnit cu = parse(code);
        cu.accept(visitor, null);

        // At end of method (outside if block), innerMsg should not be in scope
        assertThat(visitor.isVariableInScopeAtMethodEnd("testScope", "innerMsg")).isFalse();
        assertThat(visitor.isVariableInScopeAtMethodEnd("testScope", "outer")).isTrue();
    }

    @Test
    @DisplayName("UTC-S1.2-US02-022 & FTC-S1.2-012: Try-with-resources variables scoped to try block")
    void testTryWithResourcesScoped() {
        String code = """
                package com.example;
                import java.io.*;
                public class ResourceTest {
                    public void readData() throws IOException {
                        try (BufferedReader reader = new BufferedReader(null)) {
                            reader.readLine();
                        }
                    }
                }
                """;

        CompilationUnit cu = parse(code);
        cu.accept(visitor, null);

        assertThat(visitor.isVariableInScopeInsideBlock("readData", "reader")).isTrue();
        assertThat(visitor.isVariableInScopeAtMethodEnd("readData", "reader")).isFalse();
    }

    @Test
    @DisplayName("UTC-S1.2-US02-023 & FTC-S1.2-012: Catch block parameter scoped to catch clause")
    void testCatchBlockParameterScoped() {
        String code = """
                package com.example;
                import java.io.*;
                public class CatchTest {
                    public void handle() {
                        try {
                            int x = 1;
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                """;

        CompilationUnit cu = parse(code);
        cu.accept(visitor, null);

        assertThat(visitor.isVariableInScopeInsideBlock("handle", "ex")).isTrue();
        assertThat(visitor.isVariableInScopeAtMethodEnd("handle", "ex")).isFalse();
    }

    @Test
    @DisplayName("UTC-S1.2-US02-024 & FTC-S1.2-011: Local variable shadows class field")
    void testLocalVariableShadowsField() {
        String code = """
                package com.example;
                public class ShadowTest {
                    private Long id;

                    public void update() {
                        String id = "123";
                        System.out.println(id);
                    }
                }
                """;

        CompilationUnit cu = parse(code);
        cu.accept(visitor, null);

        assertThat(visitor.getVariableTypeAtMethod("update", "id")).contains("String");
    }

    @Test
    @DisplayName("FTC-S1.2-010: Variable shadowing in nested block scope")
    void testNestedBlockVariableShadowing() {
        String code = """
                package com.example;
                public class NestedBlockShadowTest {
                    public void compute() {
                        Number x = 10;
                        if (true) {
                            Double x = 20.0;
                        }
                    }
                }
                """;

        CompilationUnit cu = parse(code);
        cu.accept(visitor, null);

        // Inside if block, type is Double; at end of method, restored to Number
        assertThat(visitor.getVariableTypeInsideBlock("compute", "x")).contains("Double");
        assertThat(visitor.getVariableTypeAtMethodEnd("compute", "x")).contains("Number");
    }
}