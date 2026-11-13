package com.codecontext.core.security.rule;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.finding.RuleMetadata;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.List;

/**
 * Architectural demonstration rule for Milestone 1 verification.
 * Demonstrates AST traversal, source location tracking, and finding generation.
 * NOTE: This is an architectural fixture and does NOT represent a production vulnerability rule.
 */
public class DemonstrationSecurityRule extends AstVisitorSecurityRule {

    public static final String RULE_ID = "DEMO001";
    public static final String TARGET_METHOD_NAME = "codecontextDemoHook";

    private static final RuleMetadata METADATA = RuleMetadata.of(
        RULE_ID,
        "DemonstrationAstMethodCallRule",
        FindingCategory.CODE_QUALITY,
        FindingSeverity.INFO,
        "Milestone 1 architectural demonstration rule verifying AST method invocation detection.",
        "Demonstrates AST traversal, exact source range retention, and finding generation. Not a security vulnerability check."
    );

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    protected void visitMethodCall(MethodCallExpr call, CompilationUnitContext context, List<Finding> findings) {
        if (TARGET_METHOD_NAME.equals(call.getNameAsString())) {
            findings.add(context.createFinding(
                METADATA,
                "Detected demonstration hook invocation: " + call.getNameAsString() + "()",
                call,
                call.toString(),
                "Remove demonstration hook invocation before production release."
            ));
        }
    }
}

