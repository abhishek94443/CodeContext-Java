package com.codecontext.core.security.crypto;

import com.codecontext.core.security.rule.CompilationUnitContext;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic value resolver for cryptographic API arguments.
 * Resolves compile-time constant strings, local variables, simple linear assignments,
 * string concatenations, and static final constants while strictly respecting lexical scope,
 * source ordering, and control-flow boundaries.
 */
public final class LimitedExpressionResolver {

    private static final int MAX_RECURSION_DEPTH = 8;

    private LimitedExpressionResolver() {
        // Utility class
    }

    /**
     * Resolves an expression to a string value if statically determinable.
     *
     * @param expr     The expression to resolve.
     * @param context  The compilation unit context.
     * @param callSite The AST node representing the invocation site (for order and scope checks).
     * @return Result of the static resolution.
     */
    public static ExpressionResolution resolve(Expression expr, CompilationUnitContext context, Node callSite) {
        if (expr == null) {
            return ExpressionResolution.unknown("", "Null expression");
        }
        return resolveInternal(expr, context, callSite, 0, new HashSet<>());
    }

    private static ExpressionResolution resolveInternal(
            Expression expr,
            CompilationUnitContext context,
            Node callSite,
            int depth,
            Set<String> visitedVariables
    ) {
        if (depth > MAX_RECURSION_DEPTH) {
            return ExpressionResolution.unknown(expr.toString(), "Exceeded maximum recursion depth");
        }

        // 1. Direct String Literal
        if (expr instanceof StringLiteralExpr str) {
            return ExpressionResolution.resolved(str.getValue(), expr.toString(), "Direct string literal");
        }

        // 2. Parenthesized Expression: (expr)
        if (expr instanceof EnclosedExpr enclosed) {
            return resolveInternal(enclosed.getInner(), context, callSite, depth + 1, visitedVariables);
        }

        // 3. Simple Type Cast: (String) expr
        if (expr instanceof CastExpr cast) {
            return resolveInternal(cast.getExpression(), context, callSite, depth + 1, visitedVariables);
        }

        // 4. Binary Expression: String Concatenation ("a" + "b")
        if (expr instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            ExpressionResolution left = resolveInternal(binary.getLeft(), context, callSite, depth + 1, visitedVariables);
            ExpressionResolution right = resolveInternal(binary.getRight(), context, callSite, depth + 1, visitedVariables);

            if (left.isResolved() && right.isResolved()) {
                String combined = left.resolvedValue().get() + right.resolvedValue().get();
                return ExpressionResolution.resolved(combined, expr.toString(), "Constant string concatenation");
            } else if (left.status() == ResolutionStatus.AMBIGUOUS || right.status() == ResolutionStatus.AMBIGUOUS) {
                return ExpressionResolution.ambiguous(expr.toString(), "Concatenation involves ambiguous operand");
            } else if (left.isResolved() || right.isResolved()) {
                return ExpressionResolution.partiallyResolved(expr.toString(), "Concatenation has unresolved operand");
            } else {
                return ExpressionResolution.unknown(expr.toString(), "Both concatenation operands are unknown");
            }
        }

        // 5. Identifier / Variable reference: variableName
        if (expr instanceof NameExpr nameExpr) {
            String varName = nameExpr.getNameAsString();
            if (visitedVariables.contains(varName)) {
                return ExpressionResolution.ambiguous(expr.toString(), "Circular reference detected for: " + varName);
            }
            Set<String> nextVisited = new HashSet<>(visitedVariables);
            nextVisited.add(varName);

            return resolveVariable(varName, context, callSite, depth + 1, nextVisited);
        }

        // 6. Field access reference: ClassName.FIELD or this.field
        if (expr instanceof FieldAccessExpr fieldAccess) {
            String fieldName = fieldAccess.getNameAsString();
            if (visitedVariables.contains(fieldName)) {
                return ExpressionResolution.ambiguous(expr.toString(), "Circular reference detected for field: " + fieldName);
            }
            Set<String> nextVisited = new HashSet<>(visitedVariables);
            nextVisited.add(fieldName);

            return resolveField(fieldName, context, callSite, depth + 1, nextVisited);
        }

        // 7. Method call return values, method parameters, and arbitrary expressions
        if (expr instanceof MethodCallExpr) {
            return ExpressionResolution.unknown(expr.toString(), "Dynamic method call cannot be evaluated statically");
        }

        return ExpressionResolution.unknown(expr.toString(), "Unsupported expression type: " + expr.getClass().getSimpleName());
    }

    private static ExpressionResolution resolveVariable(
            String varName,
            CompilationUnitContext context,
            Node callSite,
            int depth,
            Set<String> visitedVariables
    ) {
        // Step 1: Search enclosing method or block scope for local variable declarations
        Node current = callSite;
        BlockStmt enclosingBlock = null;
        while (current != null) {
            if (current instanceof BlockStmt block) {
                enclosingBlock = block;
                break;
            }
            current = current.getParentNode().orElse(null);
        }

        if (enclosingBlock != null) {
            // Check for local variable declarators inside this callable/block
            List<VariableDeclarator> declarations = new ArrayList<>();
            for (VariableDeclarator vd : enclosingBlock.findAll(VariableDeclarator.class)) {
                if (vd.getNameAsString().equals(varName)) {
                    declarations.add(vd);
                }
            }

            // Filter declarations that strictly appear BEFORE callSite
            List<VariableDeclarator> priorDeclarations = declarations.stream()
                    .filter(d -> isBefore(d, callSite))
                    .toList();

            if (!priorDeclarations.isEmpty()) {
                // Take the nearest declaration (last prior declaration in source order)
                VariableDeclarator targetDecl = priorDeclarations.get(priorDeclarations.size() - 1);

                // Now check for any reassignments to varName between targetDecl and callSite
                List<AssignExpr> assignments = enclosingBlock.findAll(AssignExpr.class).stream()
                        .filter(a -> a.getTarget() instanceof NameExpr ne && ne.getNameAsString().equals(varName))
                        .filter(a -> isBefore(targetDecl, a) && isBefore(a, callSite))
                        .toList();

                if (!assignments.isEmpty()) {
                    // Check if assignments are in conditional control flow (if, loop, switch)
                    for (AssignExpr a : assignments) {
                        if (isInsideBranchOrLoop(a)) {
                            return ExpressionResolution.ambiguous(varName, "Variable '" + varName + "' reassigned inside conditional control flow");
                        }
                    }
                    if (assignments.size() > 1) {
                        return ExpressionResolution.ambiguous(varName, "Variable '" + varName + "' has multiple reassignments");
                    }
                    // Exactly one linear assignment before callSite
                    AssignExpr singleAssignment = assignments.get(0);
                    return resolveInternal(singleAssignment.getValue(), context, singleAssignment, depth, visitedVariables);
                }

                // No reassignments between declaration and callSite: resolve initializer
                if (targetDecl.getInitializer().isPresent()) {
                    return resolveInternal(targetDecl.getInitializer().get(), context, targetDecl, depth, visitedVariables);
                } else {
                    return ExpressionResolution.unknown(varName, "Variable '" + varName + "' declared without initializer");
                }
            }

            // Check if variable is declared AFTER the call site
            boolean declaredAfter = declarations.stream().anyMatch(d -> !isBefore(d, callSite));
            if (declaredAfter) {
                return ExpressionResolution.unknown(varName, "Variable '" + varName + "' is assigned after the call site");
            }
        }

        // Step 2: Search enclosing class fields (static final constants)
        return resolveField(varName, context, callSite, depth, visitedVariables);
    }

    private static ExpressionResolution resolveField(
            String fieldName,
            CompilationUnitContext context,
            Node callSite,
            int depth,
            Set<String> visitedVariables
    ) {
        CompilationUnit cu = context.compilationUnit();
        for (ClassOrInterfaceDeclaration cid : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            for (FieldDeclaration field : cid.getFields()) {
                for (VariableDeclarator vd : field.getVariables()) {
                    if (vd.getNameAsString().equals(fieldName)) {
                        // Check if field is final (or interface constant)
                        boolean isFinal = field.isFinal() || cid.isInterface();
                        if (!isFinal) {
                            return ExpressionResolution.unknown(fieldName, "Field '" + fieldName + "' is non-final and mutable");
                        }
                        if (vd.getInitializer().isPresent()) {
                            return resolveInternal(vd.getInitializer().get(), context, vd, depth, visitedVariables);
                        } else {
                            return ExpressionResolution.unknown(fieldName, "Final field '" + fieldName + "' has no direct initializer");
                        }
                    }
                }
            }
        }

        return ExpressionResolution.unknown(fieldName, "Unresolved identifier: " + fieldName);
    }

    private static boolean isBefore(Node first, Node second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getRange().isEmpty() || second.getRange().isEmpty()) {
            return false;
        }
        var r1 = first.getRange().get();
        var r2 = second.getRange().get();

        if (r1.begin.line != r2.begin.line) {
            return r1.begin.line < r2.begin.line;
        }
        return r1.begin.column < r2.begin.column;
    }

    private static boolean isInsideBranchOrLoop(Node node) {
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof IfStmt
                    || current instanceof SwitchStmt
                    || current instanceof WhileStmt
                    || current instanceof ForStmt
                    || current instanceof ForEachStmt
                    || current instanceof DoStmt) {
                return true;
            }
            if (current instanceof MethodDeclaration) {
                break;
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }
}

