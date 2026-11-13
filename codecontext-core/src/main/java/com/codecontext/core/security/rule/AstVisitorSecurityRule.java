package com.codecontext.core.security.rule;

import com.codecontext.core.finding.Finding;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenient base class for rules that inspect AST expressions (method calls, constructor invocations).
 */
public abstract class AstVisitorSecurityRule implements SecurityRule {

    @Override
    public List<Finding> analyze(CompilationUnitContext context) {
        List<Finding> findings = new ArrayList<>();
        context.compilationUnit().accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                visitMethodCall(n, context, findings);
                super.visit(n, arg);
            }

            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                visitObjectCreation(n, context, findings);
                super.visit(n, arg);
            }
        }, null);
        return List.copyOf(findings);
    }

    /**
     * Hook invoked for every MethodCallExpr in the compilation unit.
     */
    protected void visitMethodCall(MethodCallExpr call, CompilationUnitContext context, List<Finding> findings) {
        // Subclasses override
    }

    /**
     * Hook invoked for every ObjectCreationExpr (new Foo(...)) in the compilation unit.
     */
    protected void visitObjectCreation(ObjectCreationExpr creation, CompilationUnitContext context, List<Finding> findings) {
        // Subclasses override
    }
}

