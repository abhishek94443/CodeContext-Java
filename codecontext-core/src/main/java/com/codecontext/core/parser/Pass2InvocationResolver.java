package com.codecontext.core.parser;

import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.SymbolLookup;
import com.codecontext.core.model.*;
import com.codecontext.core.resolver.ResolutionChain;
import com.codecontext.core.resolver.ResolutionContext;
import com.codecontext.core.resolver.ResolutionResult;
import com.codecontext.core.scope.LexicalScopeStack;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Path;
import java.util.*;

/**
 * Pass 2 Invocation Resolver.
 * Performs AST body traversal, tracks lexical scope, resolves method/constructor call targets
 * via the five-handler Chain of Responsibility, and emits complete ParsedCompilationUnit records.
 */
public class Pass2InvocationResolver {

    private final SymbolLookup symbolLookup;
    private final TypeHierarchyIndex hierarchyIndex;
    private final ResolutionChain resolutionChain;

    public Pass2InvocationResolver(SymbolLookup symbolLookup,
                                   TypeHierarchyIndex hierarchyIndex,
                                   ResolutionChain resolutionChain) {
        this.symbolLookup = Objects.requireNonNull(symbolLookup, "symbolLookup cannot be null");
        this.hierarchyIndex = Objects.requireNonNull(hierarchyIndex, "hierarchyIndex cannot be null");
        this.resolutionChain = Objects.requireNonNull(resolutionChain, "resolutionChain cannot be null");
    }

    public ParsedCompilationUnit resolve(Path path, String sourceCode) {
        return resolve(path, sourceCode, null, null);
    }

    public ParsedCompilationUnit resolve(
            Path path,
            String sourceCode,
            com.codecontext.core.security.SecurityAnalysisEngine securityEngine,
            List<com.codecontext.core.finding.Finding> findingSink
    ) {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        JavaParser parser = new JavaParser(config);

        ParseResult<CompilationUnit> parseResult = parser.parse(sourceCode);
        List<String> parseErrors = parseResult.getProblems().stream()
                .map(Problem::getMessage)
                .toList();

        if (parseResult.getResult().isEmpty()) {
            return new ParsedCompilationUnit(
                    path,
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    new ResolutionMetrics(0, 0, 0, 0),
                    parseErrors
            );
        }

        CompilationUnit cu = parseResult.getResult().get();
        if (securityEngine != null && findingSink != null) {
            findingSink.addAll(securityEngine.analyzeCompilationUnit(path, sourceCode, cu, Optional.of(symbolLookup)));
        }

        String packageName = cu.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .orElse("");

        List<String> importStrings = cu.getImports().stream()
                .map(imp -> imp.getNameAsString() + (imp.isAsterisk() ? ".*" : ""))
                .toList();

        List<String> explicitImports = new ArrayList<>();
        List<String> wildcardImports = new ArrayList<>();
        for (String imp : importStrings) {
            if (imp.endsWith(".*")) {
                wildcardImports.add(imp);
            } else {
                explicitImports.add(imp);
            }
        }

        Set<String> sameFileTypes = new HashSet<>();
        for (TypeDeclaration<?> td : cu.getTypes()) {
            String fqcn = packageName.isBlank() ? td.getNameAsString() : packageName + "." + td.getNameAsString();
            sameFileTypes.add(fqcn);
            for (BodyDeclaration<?> member : td.getMembers()) {
                if (member instanceof TypeDeclaration<?> inner) {
                    sameFileTypes.add(fqcn + "$" + inner.getNameAsString());
                }
            }
        }

        List<InvocationReference> invocations = new ArrayList<>();
        InvocationVisitor visitor = new InvocationVisitor(
                packageName,
                sameFileTypes,
                explicitImports,
                wildcardImports,
                path,
                invocations
        );

        cu.accept(visitor, null);

        int total = invocations.size();
        int internal = (int) invocations.stream().filter(InvocationReference::isResolvedLocally).count();
        int unresolved = visitor.unresolvedCount;
        int external = total - internal - unresolved;

        ResolutionMetrics metrics = new ResolutionMetrics(total, internal, external, unresolved);

        return new ParsedCompilationUnit(
                path,
                packageName,
                importStrings,
                List.of(),
                invocations,
                metrics,
                parseErrors
        );
    }

    private class InvocationVisitor extends VoidVisitorAdapter<Void> {
        private final String packageName;
        private final Set<String> sameFileTypes;
        private final List<String> explicitImports;
        private final List<String> wildcardImports;
        private final List<InvocationReference> invocations;
        private final Path sourcePath;

        private final LexicalScopeStack scopeStack = new LexicalScopeStack();
        private final Map<String, String> fieldTypes = new HashMap<>();

        private int unresolvedCount;
        private String currentTypeFqcn = "";
        private String currentMethodName = "";

        InvocationVisitor(String packageName,
                          Set<String> sameFileTypes,
                          List<String> explicitImports,
                          List<String> wildcardImports,
                          Path sourcePath,
                          List<InvocationReference> invocations) {
            this.packageName = packageName;
            this.sameFileTypes = sameFileTypes;
            this.explicitImports = explicitImports;
            this.wildcardImports = wildcardImports;
            this.invocations = invocations;
            this.sourcePath = sourcePath;
        }

        private ResolutionContext createContext(String targetName) {
            return new ResolutionContext(
                    targetName,
                    packageName,
                    sameFileTypes,
                    explicitImports,
                    wildcardImports,
                    symbolLookup,
                    hierarchyIndex
            );
        }

        private SourceRange extractRange(com.github.javaparser.ast.Node node) {
            if (node.getRange().isPresent()) {
                var r = node.getRange().get();
                return new SourceRange(r.begin.line, r.begin.column, r.end.line, r.end.column);
            }
            return new SourceRange(1, 1, 1, 1);
        }

        private void visitType(TypeDeclaration<?> type, Runnable traversal) {
            String previousType = currentTypeFqcn;
            String previousMethod = currentMethodName;
            Map<String, String> previousFields = new HashMap<>(fieldTypes);
            currentTypeFqcn = previousType.isBlank()
                    ? (packageName.isBlank() ? type.getNameAsString() : packageName + "." + type.getNameAsString())
                    : previousType + "$" + type.getNameAsString();
            currentMethodName = "";
            fieldTypes.clear();
            for (FieldDeclaration field : type.getFields())
                for (VariableDeclarator variable : field.getVariables())
                    fieldTypes.put(variable.getNameAsString(), variable.getTypeAsString());
            scopeStack.push();
            try { traversal.run(); }
            finally {
                scopeStack.pop();
                fieldTypes.clear();
                fieldTypes.putAll(previousFields);
                currentTypeFqcn = previousType;
                currentMethodName = previousMethod;
            }
        }

        @Override public void visit(ClassOrInterfaceDeclaration n, Void arg) { visitType(n, () -> super.visit(n, arg)); }
        @Override public void visit(RecordDeclaration n, Void arg) { visitType(n, () -> super.visit(n, arg)); }
        @Override public void visit(EnumDeclaration n, Void arg) { visitType(n, () -> super.visit(n, arg)); }
        @Override public void visit(AnnotationDeclaration n, Void arg) { visitType(n, () -> super.visit(n, arg)); }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            String prevMethod = currentMethodName;
            currentMethodName = n.getNameAsString();

            scopeStack.push();
            try {
                for (Parameter p : n.getParameters()) {
                    scopeStack.declare(p.getNameAsString(), p.getTypeAsString());
                }
                super.visit(n, arg);
            } finally {
                scopeStack.pop();
                currentMethodName = prevMethod;
            }
        }

        @Override
        public void visit(ConstructorDeclaration n, Void arg) {
            String prevMethod = currentMethodName;
            currentMethodName = "<init>";

            scopeStack.push();
            try {
                for (Parameter p : n.getParameters()) {
                    scopeStack.declare(p.getNameAsString(), p.getTypeAsString());
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
            try {
                super.visit(n, arg);
            } finally {
                scopeStack.pop();
            }
        }

        @Override
        public void visit(VariableDeclarator n, Void arg) {
            if (!(n.getParentNode().orElse(null) instanceof FieldDeclaration))
                scopeStack.declare(n.getNameAsString(), n.getTypeAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(TryStmt n, Void arg) {
            scopeStack.push();
            try {
                for (var resource : n.getResources()) {
                    if (resource.isVariableDeclarationExpr()) {
                        for (VariableDeclarator vd : resource.asVariableDeclarationExpr().getVariables()) {
                            scopeStack.declare(vd.getNameAsString(), vd.getTypeAsString());
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
                scopeStack.declare(n.getParameter().getNameAsString(), n.getParameter().getTypeAsString());
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
                    scopeStack.declare(vd.getNameAsString(), vd.getTypeAsString());
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
                            scopeStack.declare(vd.getNameAsString(), vd.getTypeAsString());
                        }
                    }
                }
                super.visit(n, arg);
            } finally {
                scopeStack.pop();
            }
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            String typeName = n.getType().getNameAsString();
            ResolutionResult resolved = resolutionChain.resolve(createContext(typeName));

            invocations.add(new InvocationReference(
                    currentTypeFqcn,
                    currentMethodName,
                    resolved.resolvedFqcn(),
                    "<init>",
                    "<init>()",
                    InvocationKind.CONSTRUCTOR_CALL,
                    extractRange(n),
                    resolved.isResolvedLocally()
            ));

            super.visit(n, arg);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            String targetType = currentTypeFqcn;
            InvocationKind kind = InvocationKind.METHOD_CALL;
            boolean unsupportedReceiver = false;

            if (n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                if (scope.isThisExpr()) {
                    targetType = currentTypeFqcn;
                } else if (scope.isSuperExpr()) {
                    targetType = hierarchyIndex.findDirectSuperclass(currentTypeFqcn).orElse(currentTypeFqcn);
                    kind = InvocationKind.SUPER_CALL;
                } else if (scope.isNameExpr()) {
                    String scopeName = scope.asNameExpr().getNameAsString();
                    Optional<String> localType = scopeStack.lookup(scopeName);
                    if (localType.isPresent()) {
                        targetType = localType.get();
                    } else if (fieldTypes.containsKey(scopeName)) {
                        targetType = fieldTypes.get(scopeName);
                    } else {
                        // Scope name could be a class name for a static method call (e.g. System, LoggerFactory)
                        targetType = scopeName;
                        kind = InvocationKind.STATIC_METHOD_CALL;
                    }
                } else {
                    targetType = scope.toString();
                    unsupportedReceiver = scope.isMethodCallExpr() || scope.isObjectCreationExpr();
                }
            }

            ResolutionResult resolved = symbolLookup.findByFqcn(targetType).isPresent()
                    ? new ResolutionResult(targetType, true, "ExactType")
                    : resolutionChain.resolve(createContext(targetType));
            String finalTargetFqcn = resolved.resolvedFqcn();
            String signature = methodName + "(?)";
            if (unsupportedReceiver) {
                unresolvedCount++;
                finalTargetFqcn = "<unresolved>:" + targetType;
            } else if (resolved.isResolvedLocally()) {
                // Only uniquely named methods in a known superclass chain are supported.
                // No overload selection, generic inference, or dynamic dispatch is claimed.
                Set<String> seen = new HashSet<>();
                String candidate = finalTargetFqcn;
                while (seen.add(candidate) && seen.size() <= 20) {
                    var definition = symbolLookup.findByFqcn(candidate);
                    if (definition.isEmpty()) break;
                    var methods = definition.get().methods().stream().filter(m -> m.name().equals(methodName)).toList();
                    if (!methods.isEmpty()) {
                        if (methods.size() == 1 && methods.get(0).parameterTypes().size() == n.getArguments().size()) {
                            finalTargetFqcn = candidate;
                            signature = methods.get(0).signature();
                        }
                        break;
                    }
                    // Imports for other compilation units are not available here.
                    if (!definition.get().sourceFilePath().equals(sourcePath)) break;
                    var parent = hierarchyIndex.findDirectSuperclass(candidate);
                    if (parent.isEmpty()) break;
                    String parentName = parent.get();
                    candidate = parentName.contains(".") ? parentName
                            : explicitImports.stream().filter(i -> i.endsWith("." + parentName)).findFirst()
                                .orElseGet(() -> resolutionChain.resolve(createContext(parentName)).resolvedFqcn());
                }
            }

            invocations.add(new InvocationReference(
                    currentTypeFqcn,
                    currentMethodName,
                    finalTargetFqcn,
                    methodName,
                    signature,
                    kind,
                    extractRange(n),
                    resolved.isResolvedLocally() && !unsupportedReceiver
            ));

            super.visit(n, arg);
        }
    }
}