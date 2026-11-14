package com.codecontext.core.security.crypto;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.security.rule.CompilationUnitContext;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers and validates cryptographic API invocations against supported JCA/JCE/JSSE classes.
 * Accurately verifies API ownership using imports, FQCNs, package context, and local type collision checks
 * to prevent false identification of custom classes (e.g. FakeCipher).
 */
public final class CryptoApiDetector {

    private CryptoApiDetector() {
        // Utility class
    }

    /**
     * Inspects a MethodCallExpr and returns a CryptoApiUsage if it targets a supported cryptographic API.
     */
    public static Optional<CryptoApiUsage> detectMethodCall(MethodCallExpr call, CompilationUnitContext context) {
        if (call == null || context == null) {
            return Optional.empty();
        }

        String methodName = call.getNameAsString();
        if (!methodName.equals("getInstance")) {
            return Optional.empty();
        }

        CompilationUnit cu = context.compilationUnit();
        Optional<Expression> scopeOpt = call.getScope();

        OwnerResolution resolution = resolveApiOwner(scopeOpt, cu);
        if (resolution == null || resolution.owner() == null) {
            return Optional.empty();
        }

        CryptoApiOwner owner = resolution.owner();
        AnalysisConfidence ownerConfidence = resolution.confidence();

        // Extract first argument (algorithm / transformation)
        if (call.getArguments().isEmpty()) {
            return Optional.empty();
        }

        Expression arg0 = call.getArgument(0);
        ExpressionResolution exprResolution = LimitedExpressionResolver.resolve(arg0, context, call);

        Optional<CryptoAlgorithm> algorithm = exprResolution.isResolved()
                ? Optional.of(CryptoAlgorithm.parse(exprResolution.resolvedValue().get()))
                : Optional.empty();

        SourceRange range = context.toSourceRange(call);

        Map<String, String> properties = new HashMap<>();
        properties.put("scope", scopeOpt.map(Expression::toString).orElse("<static-import>"));
        call.findAncestor(TypeDeclaration.class).ifPresent(t -> {
            @SuppressWarnings("unchecked")
            Optional<String> fqOpt = ((TypeDeclaration<?>) t).getFullyQualifiedName();
            String fqcn = fqOpt.orElseGet(() -> {
                String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
                return pkg + t.getNameAsString();
            });
            properties.put("enclosingClass", fqcn);
        });
        call.findAncestor(MethodDeclaration.class).ifPresent(m -> {
            properties.put("enclosingMethod", m.getNameAsString());
        });

        return Optional.of(new CryptoApiUsage(
                owner,
                methodName,
                context.filePath(),
                range,
                arg0.toString(),
                exprResolution,
                algorithm,
                ownerConfidence,
                Map.copyOf(properties)
        ));
    }

    /**
     * Inspects an ObjectCreationExpr (e.g. new SecureRandom()) and returns a CryptoApiUsage if supported.
     */
    public static Optional<CryptoApiUsage> detectObjectCreation(ObjectCreationExpr creation, CompilationUnitContext context) {
        if (creation == null || context == null) {
            return Optional.empty();
        }

        String typeName = creation.getType().getNameWithScope();
        CompilationUnit cu = context.compilationUnit();

        CryptoApiOwner targetOwner = CryptoApiOwner.SECURE_RANDOM;
        AnalysisConfidence confidence = null;

        if (typeName.equals(targetOwner.fqcn())) {
            confidence = AnalysisConfidence.HIGH;
        } else if (typeName.equals(targetOwner.simpleName())) {
            // Check for collision with local type
            if (hasLocalTypeCollision(cu, targetOwner.simpleName())) {
                return Optional.empty();
            }
            if (hasExplicitImport(cu, targetOwner.fqcn())) {
                confidence = AnalysisConfidence.HIGH;
            } else if (hasWildcardImport(cu, targetOwner.packageName())) {
                confidence = AnalysisConfidence.HIGH;
            } else if (isPackageMatch(cu, targetOwner.packageName())) {
                confidence = AnalysisConfidence.HIGH;
            }
        }

        if (confidence == null) {
            return Optional.empty();
        }

        SourceRange range = context.toSourceRange(creation);
        String argExpr = creation.getArguments().isEmpty() ? "" : creation.getArgument(0).toString();
        ExpressionResolution resolution = creation.getArguments().isEmpty()
                ? ExpressionResolution.resolved("DEFAULT", "", "Default constructor")
                : LimitedExpressionResolver.resolve(creation.getArgument(0), context, creation);

        Map<String, String> properties = new HashMap<>();
        properties.put("constructor", "true");
        creation.findAncestor(TypeDeclaration.class).ifPresent(t -> {
            @SuppressWarnings("unchecked")
            Optional<String> fqOpt = ((TypeDeclaration<?>) t).getFullyQualifiedName();
            String fqcn = fqOpt.orElseGet(() -> {
                String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
                return pkg + t.getNameAsString();
            });
            properties.put("enclosingClass", fqcn);
        });
        creation.findAncestor(MethodDeclaration.class).ifPresent(m -> {
            properties.put("enclosingMethod", m.getNameAsString());
        });

        return Optional.of(new CryptoApiUsage(
                targetOwner,
                "<init>",
                context.filePath(),
                range,
                argExpr,
                resolution,
                Optional.empty(),
                confidence,
                Map.copyOf(properties)
        ));
    }

    /**
     * Scans an entire CompilationUnitContext and discovers all supported cryptographic API usages.
     */
    public static List<CryptoApiUsage> detectAll(CompilationUnitContext context) {
        if (context == null) {
            return List.of();
        }
        List<CryptoApiUsage> usages = new ArrayList<>();
        CompilationUnit cu = context.compilationUnit();

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            detectMethodCall(call, context).ifPresent(usages::add);
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            detectObjectCreation(creation, context).ifPresent(usages::add);
        }

        return usages;
    }

    private static OwnerResolution resolveApiOwner(Optional<Expression> scopeOpt, CompilationUnit cu) {
        if (scopeOpt.isPresent()) {
            Expression scope = scopeOpt.get();

            // 1. Simple name expression: Cipher.getInstance(...)
            if (scope instanceof NameExpr nameExpr) {
                String simpleName = nameExpr.getNameAsString();
                Optional<CryptoApiOwner> ownerOpt = CryptoApiOwner.findBySimpleName(simpleName);
                if (ownerOpt.isEmpty()) {
                    // Not a known JCA simple name (e.g. FakeCipher)
                    return null;
                }
                CryptoApiOwner owner = ownerOpt.get();

                // Check local type collision in same file (class Cipher { ... })
                if (hasLocalTypeCollision(cu, simpleName)) {
                    return null;
                }

                // Check explicit imports
                for (ImportDeclaration imp : cu.getImports()) {
                    if (!imp.isStatic() && !imp.isAsterisk()) {
                        String importedName = imp.getNameAsString();
                        if (importedName.endsWith("." + simpleName)) {
                            if (importedName.equals(owner.fqcn())) {
                                return new OwnerResolution(owner, AnalysisConfidence.HIGH);
                            } else {
                                // Explicitly imports another class with the same name (e.g. com.fake.Cipher)
                                return null;
                            }
                        }
                    }
                }

                // Check wildcard imports (e.g. import javax.crypto.*;)
                if (hasWildcardImport(cu, owner.packageName())) {
                    if (hasConflictingWildcardImports(cu, owner.packageName())) {
                        return new OwnerResolution(owner, AnalysisConfidence.LOW);
                    }
                    return new OwnerResolution(owner, AnalysisConfidence.HIGH);
                }

                // Check package declaration
                if (isPackageMatch(cu, owner.packageName())) {
                    return new OwnerResolution(owner, AnalysisConfidence.HIGH);
                }

                // If not imported and not in same package, JCA classes cannot be used unqualified
                return null;
            }

            // 2. Fully qualified name expression: javax.crypto.Cipher.getInstance(...)
            if (scope instanceof FieldAccessExpr fieldAccess) {
                String fqcn = fieldAccess.toString();
                Optional<CryptoApiOwner> ownerOpt = CryptoApiOwner.findByFqcn(fqcn);
                if (ownerOpt.isPresent()) {
                    return new OwnerResolution(ownerOpt.get(), AnalysisConfidence.HIGH);
                }
                return null;
            }

            return null;
        } else {
            // Scope is absent: Static import (getInstance("..."))
            for (ImportDeclaration imp : cu.getImports()) {
                if (imp.isStatic()) {
                    String imported = imp.getNameAsString();
                    for (CryptoApiOwner owner : CryptoApiOwner.values()) {
                        if (imported.equals(owner.fqcn() + ".getInstance") || (imp.isAsterisk() && imported.equals(owner.fqcn()))) {
                            return new OwnerResolution(owner, AnalysisConfidence.HIGH);
                        }
                    }
                }
            }
            return null;
        }
    }

    private static boolean hasLocalTypeCollision(CompilationUnit cu, String simpleName) {
        for (TypeDeclaration<?> td : cu.findAll(TypeDeclaration.class)) {
            if (td.getNameAsString().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConflictingWildcardImports(CompilationUnit cu, String standardPackage) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isStatic() && imp.isAsterisk()) {
                String pkg = imp.getNameAsString();
                if (!pkg.equals(standardPackage)
                        && !pkg.equals("java.lang")
                        && !pkg.equals("java.util")
                        && !pkg.equals("java.io")
                        && !pkg.equals("java.nio")
                        && !pkg.equals("java.net")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasExplicitImport(CompilationUnit cu, String fqcn) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isStatic() && !imp.isAsterisk() && imp.getNameAsString().equals(fqcn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWildcardImport(CompilationUnit cu, String packageName) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isStatic() && imp.isAsterisk() && imp.getNameAsString().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPackageMatch(CompilationUnit cu, String packageName) {
        return cu.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .map(p -> p.equals(packageName))
                .orElse(false);
    }

    private record OwnerResolution(CryptoApiOwner owner, AnalysisConfidence confidence) {}
}

