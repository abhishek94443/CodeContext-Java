package com.codecontext.core.parser;

import com.codecontext.core.scope.LexicalScopeStack;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.*;

/**
 * AST Visitor implementing lexical scope stack tracking across method declarations,
 * blocks, try-with-resources, catch clauses, and loops.
 */
public class ReferenceVisitor extends VoidVisitorAdapter<Void> {

    private final LexicalScopeStack scopeStack = new LexicalScopeStack();
    private final Map<String, String> fieldTypes = new HashMap<>();

    // Diagnostic / snapshot maps for testing and Pass 2 resolution
    private final Map<String, Map<String, String>> methodScopeSnapshots = new HashMap<>();
    private final Map<String, Map<String, String>> insideBlockSnapshots = new HashMap<>();
    private final Map<String, Map<String, String>> methodEndSnapshots = new HashMap<>();

    private String currentMethodName = null;
    private final Set<String> allDeclaredVarsInMethod = new HashSet<>();

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        for (FieldDeclaration field : n.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                fieldTypes.put(var.getNameAsString(), var.getTypeAsString());
            }
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(RecordDeclaration n, Void arg) {
        for (FieldDeclaration field : n.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                fieldTypes.put(var.getNameAsString(), var.getTypeAsString());
            }
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        String prevMethod = currentMethodName;
        currentMethodName = n.getNameAsString();
        allDeclaredVarsInMethod.clear();

        scopeStack.push();
        try {
            // Register parameters
            Map<String, String> paramSnapshot = new HashMap<>();
            for (Parameter p : n.getParameters()) {
                String pName = p.getNameAsString();
                String pType = p.getTypeAsString();
                scopeStack.declare(pName, pType);
                allDeclaredVarsInMethod.add(pName);
                paramSnapshot.put(pName, pType);
            }
            methodScopeSnapshots.put(currentMethodName, paramSnapshot);

            super.visit(n, arg);

        } finally {
            scopeStack.pop();
            currentMethodName = prevMethod;
        }
    }

    @Override
    public void visit(ConstructorDeclaration n, Void arg) {
        String prevMethod = currentMethodName;
        currentMethodName = n.getNameAsString();
        allDeclaredVarsInMethod.clear();

        scopeStack.push();
        try {
            for (Parameter p : n.getParameters()) {
                scopeStack.declare(p.getNameAsString(), p.getTypeAsString());
                allDeclaredVarsInMethod.add(p.getNameAsString());
            }
            super.visit(n, arg);
        } finally {
            scopeStack.pop();
            currentMethodName = prevMethod;
        }
    }

    @Override
    public void visit(BlockStmt n, Void arg) {
        scopeStack.push();
        boolean isMethodBody = n.getParentNode().filter(p -> p instanceof MethodDeclaration).isPresent();

        try {
            super.visit(n, arg);

            if (isMethodBody && currentMethodName != null) {
                // Snapshot scope at end of method body before popping its block frame
                Map<String, String> endSnapshot = new HashMap<>();
                for (String varName : allDeclaredVarsInMethod) {
                    scopeStack.lookup(varName).ifPresent(type -> endSnapshot.put(varName, type));
                }
                methodEndSnapshots.put(currentMethodName, endSnapshot);
            }
        } finally {
            scopeStack.pop();
        }
    }

    @Override
    public void visit(VariableDeclarator n, Void arg) {
        String name = n.getNameAsString();
        String type = n.getTypeAsString();
        scopeStack.declare(name, type);
        allDeclaredVarsInMethod.add(name);

        if (currentMethodName != null) {
            Map<String, String> methodSnapshot = methodScopeSnapshots.computeIfAbsent(currentMethodName, k -> new HashMap<>());
            methodSnapshot.put(name, type);

            // If we are inside an inner/nested block (depth > 2: root=1, method=2, block>=3)
            if (scopeStack.depth() > 2) {
                Map<String, String> blockSnapshot = insideBlockSnapshots.computeIfAbsent(currentMethodName, k -> new HashMap<>());
                blockSnapshot.put(name, type);
            }
        }

        super.visit(n, arg);
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        scopeStack.push();
        try {
            // Register try-with-resources variables
            for (var resource : n.getResources()) {
                if (resource.isVariableDeclarationExpr()) {
                    for (VariableDeclarator vd : resource.asVariableDeclarationExpr().getVariables()) {
                        String name = vd.getNameAsString();
                        String type = vd.getTypeAsString();
                        scopeStack.declare(name, type);
                        allDeclaredVarsInMethod.add(name);
                        if (currentMethodName != null) {
                            insideBlockSnapshots.computeIfAbsent(currentMethodName, k -> new HashMap<>())
                                    .put(name, type);
                        }
                    }
                }
            }
            super.visit(n, arg);
        } finally {
            scopeStack.pop();
        }
    }

    @Override
    public void visit(CatchClause n, Void arg) {
        scopeStack.push();
        try {
            String paramName = n.getParameter().getNameAsString();
            String paramType = n.getParameter().getTypeAsString();
            scopeStack.declare(paramName, paramType);
            allDeclaredVarsInMethod.add(paramName);
            if (currentMethodName != null) {
                insideBlockSnapshots.computeIfAbsent(currentMethodName, k -> new HashMap<>())
                        .put(paramName, paramType);
            }
            super.visit(n, arg);
        } finally {
            scopeStack.pop();
        }
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        scopeStack.push();
        try {
            for (VariableDeclarator vd : n.getVariable().getVariables()) {
                String name = vd.getNameAsString();
                String type = vd.getTypeAsString();
                scopeStack.declare(name, type);
                allDeclaredVarsInMethod.add(name);
            }
            super.visit(n, arg);
        } finally {
            scopeStack.pop();
        }
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        scopeStack.push();
        try {
            for (var init : n.getInitialization()) {
                if (init.isVariableDeclarationExpr()) {
                    for (VariableDeclarator vd : init.asVariableDeclarationExpr().getVariables()) {
                        String name = vd.getNameAsString();
                        String type = vd.getTypeAsString();
                        scopeStack.declare(name, type);
                        allDeclaredVarsInMethod.add(name);
                    }
                }
            }
            super.visit(n, arg);
        } finally {
            scopeStack.pop();
        }
    }

    /**
     * Resolve variable type by checking innermost lexical scope, then falling back to class fields.
     */
    public Optional<String> resolveVariable(String name) {
        Optional<String> localType = scopeStack.lookup(name);
        if (localType.isPresent()) {
            return localType;
        }
        return Optional.ofNullable(fieldTypes.get(name));
    }

    public Optional<String> getVariableTypeAtMethod(String methodName, String varName) {
        Map<String, String> snapshot = methodScopeSnapshots.get(methodName);
        if (snapshot != null && snapshot.containsKey(varName)) {
            return Optional.of(snapshot.get(varName));
        }
        return Optional.ofNullable(fieldTypes.get(varName));
    }

    public boolean isVariableInScopeAtMethodEnd(String methodName, String varName) {
        Map<String, String> snapshot = methodEndSnapshots.get(methodName);
        return snapshot != null && snapshot.containsKey(varName);
    }

    public boolean isVariableInScopeInsideBlock(String methodName, String varName) {
        Map<String, String> snapshot = insideBlockSnapshots.get(methodName);
        return snapshot != null && snapshot.containsKey(varName);
    }

    public Optional<String> getVariableTypeInsideBlock(String methodName, String varName) {
        Map<String, String> snapshot = insideBlockSnapshots.get(methodName);
        if (snapshot != null) {
            return Optional.ofNullable(snapshot.get(varName));
        }
        return Optional.empty();
    }

    public Optional<String> getVariableTypeAtMethodEnd(String methodName, String varName) {
        Map<String, String> snapshot = methodEndSnapshots.get(methodName);
        if (snapshot != null) {
            return Optional.ofNullable(snapshot.get(varName));
        }
        return Optional.empty();
    }
}