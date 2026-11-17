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

            String qualifier = fieldAccess.getScope().toString();
            var owners = context.compilationUnit().findAll(com.github.javaparser.ast.body.TypeDeclaration.class).stream()
                    .map(t -> (com.github.javaparser.ast.body.TypeDeclaration<?>) t)
                    .filter(t -> t.getFullyQualifiedName().map(qualifier::equals).orElse(false)
                            || t.getNameAsString().equals(qualifier)).toList();
            if (qualifier.equals("this")) {
                var owner = callSite.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class);
                return owner.isPresent() ? resolveDeclaredField(owner.get(), fieldName, context, depth + 1, nextVisited)
                        : ExpressionResolution.unknown(expr.toString(), "No enclosing type");
            }
            if (owners.size() != 1 || hasValueBinding(qualifier, callSite))
                return ExpressionResolution.unknown(expr.toString(), "Unresolved or ambiguous constant owner");
            var owner = owners.get(0);
            var field = owner.getFieldByName(fieldName);
            if (field.isEmpty() || !(field.get().isStatic()
                    || owner instanceof ClassOrInterfaceDeclaration c && c.isInterface()))
                return ExpressionResolution.unknown(expr.toString(), "Not a known static constant");
            return resolveDeclaredField(owner, fieldName, context, depth + 1, nextVisited);
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
        for (Node current = callSite; current != null; current = current.getParentNode().orElse(null)) {
            if (current instanceof BlockStmt block) {
                var declarations = block.findAll(VariableDeclarator.class).stream()
                        .filter(v -> v.getNameAsString().equals(varName))
                        .filter(v -> v.getParentNode().filter(p -> p instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr)
                                .flatMap(Node::getParentNode).filter(p -> p instanceof com.github.javaparser.ast.stmt.ExpressionStmt)
                                .flatMap(Node::getParentNode).filter(block::equals).isPresent()).toList();
                var prior = declarations.stream().filter(v -> isBefore(v, callSite)).toList();
                if (!prior.isEmpty()) {
                    var declaration = prior.get(prior.size() - 1);
                    var assignments = block.findAll(AssignExpr.class).stream()
                            .filter(a -> a.getTarget() instanceof NameExpr n && n.getNameAsString().equals(varName))
                            .filter(a -> isBefore(declaration, a) && isBefore(a, callSite)).toList();
                    if (assignments.stream().anyMatch(a -> a.getOperator() != AssignExpr.Operator.ASSIGN))
                        return ExpressionResolution.unknown(varName, "Compound assignment is not evaluated");
                    if (assignments.stream().anyMatch(LimitedExpressionResolver::isInsideBranchOrLoop) || assignments.size() > 1)
                        return ExpressionResolution.ambiguous(varName, "Multiple or conditional assignments");
                    if (!assignments.isEmpty()) {
                        var assignment = assignments.get(0);
                        if (!assignment.findAncestor(BlockStmt.class).filter(block::equals).isPresent())
                            return ExpressionResolution.unknown(varName, "Assignment crosses a scope boundary");
                        return resolveInternal(assignment.getValue(), context, assignment, depth, visitedVariables);
                    }
                    return declaration.getInitializer()
                            .map(e -> resolveInternal(e, context, declaration, depth, visitedVariables))
                            .orElseGet(() -> ExpressionResolution.unknown(varName, "No initializer"));
                }
                if (!declarations.isEmpty()) return ExpressionResolution.unknown(varName, "Declaration follows call site");
            }
            if (current instanceof com.github.javaparser.ast.nodeTypes.NodeWithParameters<?> parameters
                    && parameters.getParameters().stream().anyMatch(p -> p.getNameAsString().equals(varName)))
                return ExpressionResolution.unknown(varName, "Runtime parameter shadows fields");
            if (current instanceof com.github.javaparser.ast.stmt.CatchClause c && c.getParameter().getNameAsString().equals(varName))
                return ExpressionResolution.unknown(varName, "Catch parameter");
            if (current instanceof ForEachStmt loop && loop.getVariable().getVariables().stream()
                    .anyMatch(v -> v.getNameAsString().equals(varName)))
                return ExpressionResolution.unknown(varName, "Loop variable");
            if (current instanceof ForStmt || current instanceof com.github.javaparser.ast.stmt.TryStmt) {
                if (current.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(varName)))
                    return ExpressionResolution.unknown(varName, "Loop or resource binding is not evaluated");
            }
            if (current instanceof com.github.javaparser.ast.body.TypeDeclaration<?> type && type.getFieldByName(varName).isPresent())
                return resolveDeclaredField(type, varName, context, depth, visitedVariables);
        }
        return ExpressionResolution.unknown(varName, "Unresolved identifier: " + varName);
    }

    private static boolean hasValueBinding(String name, Node site) {
        for (Node node = site; node != null; node = node.getParentNode().orElse(null)) {
            if (node instanceof com.github.javaparser.ast.nodeTypes.NodeWithParameters<?> parameters
                    && parameters.getParameters().stream().anyMatch(p -> p.getNameAsString().equals(name))) return true;
            if (node instanceof BlockStmt block && block.findAll(VariableDeclarator.class).stream()
                    .anyMatch(v -> v.getNameAsString().equals(name) && isBefore(v, site))) return true;
            if (node instanceof com.github.javaparser.ast.body.TypeDeclaration<?> type && type.getFieldByName(name).isPresent()) return true;
        }
        return false;
    }

    private static ExpressionResolution resolveDeclaredField(
            com.github.javaparser.ast.body.TypeDeclaration<?> owner, String name,
            CompilationUnitContext context, int depth, Set<String> visitedVariables) {
        var field = owner.getFieldByName(name);
        if (field.isEmpty()) return ExpressionResolution.unknown(name, "Unknown field on selected owner");
        if (!(field.get().isFinal() || owner instanceof ClassOrInterfaceDeclaration c && c.isInterface()))
            return ExpressionResolution.unknown(name, "Mutable field");
        var variable = field.get().getVariables().stream().filter(v -> v.getNameAsString().equals(name)).findFirst().orElseThrow();
        return variable.getInitializer().map(e -> resolveInternal(e, context, variable, depth, visitedVariables))
                .orElseGet(() -> ExpressionResolution.unknown(name, "Final field has no initializer"));
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

