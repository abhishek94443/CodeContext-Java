package com.codecontext.core.parser;

import com.codecontext.core.index.SymbolRegistry;
import com.codecontext.core.model.FieldDefinition;
import com.codecontext.core.model.MethodDefinition;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.TypeDefinition;
import com.codecontext.core.model.TypeKind;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Thread-safe visitor that parses Java compilation units and extracts TypeDefinitions into the SymbolRegistry.
 */
public class JavaParserDeclarationVisitor {

    private static final ParserConfiguration CONFIG = new ParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    /**
     * Parses the Java source string, extracts all declarations, and registers them into the SymbolRegistry.
     * Uses a thread-local parser instance to guarantee thread safety across concurrent virtual threads.
     */
    public ParseResult parseAndRegister(String code, Path filePath, SymbolRegistry registry) {
        List<String> errors = new ArrayList<>();
        List<TypeDefinition> typesDiscovered = new ArrayList<>();

        // Create independent parser instance per task to ensure absolute thread safety
        JavaParser parser = new JavaParser(CONFIG);

        com.github.javaparser.ParseResult<CompilationUnit> parseResult;
        try {
            parseResult = parser.parse(code);
        } catch (Exception e) {
            errors.add("Failed to parse file: " + e.getMessage());
            return new ParseResult(typesDiscovered, errors);
        }

        if (!parseResult.isSuccessful()) {
            parseResult.getProblems().forEach(p -> errors.add(p.getMessage()));
        }

        if (parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElse("");

            for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
                extractTypeRecursively(typeDecl, packageName, "", filePath, registry, typesDiscovered);
            }
        }

        return new ParseResult(typesDiscovered, errors);
    }

    private void extractTypeRecursively(
        TypeDeclaration<?> typeDecl,
        String packageName,
        String enclosingPrefix,
        Path filePath,
        SymbolRegistry registry,
        List<TypeDefinition> typesDiscovered
    ) {
        String simpleName = typeDecl.getNameAsString();
        String currentPrefix = enclosingPrefix.isEmpty() ? simpleName : enclosingPrefix + "$" + simpleName;
        String fqcn = packageName.isEmpty() ? currentPrefix : packageName + "." + currentPrefix;

        TypeKind kind = determineKind(typeDecl);
        SourceRange range = toSourceRange(typeDecl);

        Optional<String> superclass = Optional.empty();
        Set<String> interfaces = new HashSet<>();

        if (typeDecl instanceof ClassOrInterfaceDeclaration cid) {
            if (!cid.isInterface() && !cid.getExtendedTypes().isEmpty()) {
                superclass = Optional.of(cid.getExtendedTypes(0).getNameAsString());
            }
            if (cid.isInterface()) {
                cid.getExtendedTypes().forEach(et -> interfaces.add(et.getNameAsString()));
            } else {
                cid.getImplementedTypes().forEach(it -> interfaces.add(it.getNameAsString()));
            }
        } else if (typeDecl instanceof RecordDeclaration rd) {
            rd.getImplementedTypes().forEach(it -> interfaces.add(it.getNameAsString()));
        } else if (typeDecl instanceof EnumDeclaration ed) {
            ed.getImplementedTypes().forEach(it -> interfaces.add(it.getNameAsString()));
        }

        List<MethodDefinition> methods = extractMethods(typeDecl);
        List<FieldDefinition> fields = extractFields(typeDecl);
        Set<String> annotations = extractAnnotations(typeDecl);

        TypeDefinition typeDef = new TypeDefinition(
            fqcn,
            simpleName,
            packageName,
            kind,
            filePath,
            superclass,
            interfaces,
            methods,
            fields,
            annotations,
            range
        );

        registry.register(typeDef);
        typesDiscovered.add(typeDef);

        // Recursively extract nested / member types
        for (Node child : typeDecl.getChildNodes()) {
            if (child instanceof TypeDeclaration<?> nestedDecl) {
                extractTypeRecursively(nestedDecl, packageName, currentPrefix, filePath, registry, typesDiscovered);
            }
        }
    }

    private TypeKind determineKind(TypeDeclaration<?> typeDecl) {
        if (typeDecl instanceof RecordDeclaration) {
            return TypeKind.RECORD;
        } else if (typeDecl instanceof EnumDeclaration) {
            return TypeKind.ENUM;
        } else if (typeDecl instanceof AnnotationDeclaration) {
            return TypeKind.ANNOTATION;
        } else if (typeDecl instanceof ClassOrInterfaceDeclaration cid) {
            return cid.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
        }
        return TypeKind.CLASS;
    }

    private List<MethodDefinition> extractMethods(TypeDeclaration<?> typeDecl) {
        List<MethodDefinition> methods = new ArrayList<>();

        for (MethodDeclaration md : typeDecl.getMethods()) {
            Set<String> modifiers = new HashSet<>();
            md.getModifiers().forEach(m -> modifiers.add(m.getKeyword().asString()));

            List<String> paramTypes = md.getParameters().stream()
                .map(p -> p.getType().asString())
                .toList();

            List<String> paramNames = md.getParameters().stream()
                .map(p -> p.getNameAsString())
                .toList();

            String signature = md.getNameAsString() + "(" + String.join(",", paramTypes) + ")";

            methods.add(new MethodDefinition(
                md.getNameAsString(),
                signature,
                md.getType().asString(),
                paramTypes,
                paramNames,
                modifiers,
                false,
                toSourceRange(md)
            ));
        }

        for (ConstructorDeclaration cd : typeDecl.getConstructors()) {
            Set<String> modifiers = new HashSet<>();
            cd.getModifiers().forEach(m -> modifiers.add(m.getKeyword().asString()));

            List<String> paramTypes = cd.getParameters().stream()
                .map(p -> p.getType().asString())
                .toList();

            List<String> paramNames = cd.getParameters().stream()
                .map(p -> p.getNameAsString())
                .toList();

            String signature = cd.getNameAsString() + "(" + String.join(",", paramTypes) + ")";

            methods.add(new MethodDefinition(
                cd.getNameAsString(),
                signature,
                "void",
                paramTypes,
                paramNames,
                modifiers,
                true,
                toSourceRange(cd)
            ));
        }

        return methods;
    }

    private List<FieldDefinition> extractFields(TypeDeclaration<?> typeDecl) {
        List<FieldDefinition> fields = new ArrayList<>();

        for (FieldDeclaration fd : typeDecl.getFields()) {
            Set<String> modifiers = new HashSet<>();
            fd.getModifiers().forEach(m -> modifiers.add(m.getKeyword().asString()));
            String typeStr = fd.getElementType().asString();

            fd.getVariables().forEach(v -> {
                fields.add(new FieldDefinition(
                    v.getNameAsString(),
                    typeStr,
                    modifiers,
                    toSourceRange(v)
                ));
            });
        }

        if (typeDecl instanceof RecordDeclaration rd) {
            rd.getParameters().forEach(rc -> {
                fields.add(new FieldDefinition(
                    rc.getNameAsString(),
                    rc.getType().asString(),
                    Set.of("private", "final"),
                    toSourceRange(rc)
                ));
            });
        }

        return fields;
    }

    private Set<String> extractAnnotations(TypeDeclaration<?> typeDecl) {
        Set<String> annotations = new HashSet<>();
        for (AnnotationExpr expr : typeDecl.getAnnotations()) {
            annotations.add("@" + expr.getNameAsString());
        }
        return annotations;
    }

    private SourceRange toSourceRange(Node node) {
        if (node.getRange().isPresent()) {
            Range r = node.getRange().get();
            return new SourceRange(
                Math.max(1, r.begin.line),
                Math.max(1, r.begin.column),
                Math.max(r.begin.line, r.end.line),
                Math.max(1, r.end.column)
            );
        }
        return new SourceRange(1, 1, 1, 1);
    }
}