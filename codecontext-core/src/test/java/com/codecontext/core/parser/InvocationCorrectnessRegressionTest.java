package com.codecontext.core.parser;

import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.ConcurrentSymbolTable;
import com.codecontext.core.model.*;
import com.codecontext.core.resolver.ResolutionChain;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class InvocationCorrectnessRegressionTest {
    @Test void qualifiedExternalSuperclassDoesNotBorrowLocalMethod() {
        var unit=resolve("package p; class Base {void run(){}} class Child extends external.Base {void f(){run();}}");
        assertThat(unit.invocations()).extracting(InvocationReference::targetFqcn).doesNotContain("p.Base");
        assertThat(unit.invocations()).extracting(InvocationReference::targetSignature).containsExactly("run(?)");
    }
    private ParsedCompilationUnit resolve(String source) {
        var symbols = new ConcurrentSymbolTable();
        new JavaParserDeclarationVisitor().parseAndRegister(source,Path.of("Example.java"),symbols);
        var hierarchy = new TypeHierarchyIndex(symbols);
        symbols.allTypes().forEach(hierarchy::index);
        return new Pass2InvocationResolver(symbols,hierarchy,ResolutionChain.standardChain()).resolve(Path.of("Example.java"),source);
    }
    @Test void nestedCallerUsesDeclarationIdentity() {
        assertThat(resolve("package p; class Outer { class Inner { void f(){ f(); } } }").invocations())
                .extracting(InvocationReference::callerFqcn).containsExactly("p.Outer$Inner");
    }
    @Test void enumCallerUsesDeclarationIdentity() {
        assertThat(resolve("package p; enum E { ONE; void f(){ f(); } }").invocations())
                .extracting(InvocationReference::callerFqcn).containsExactly("p.E");
    }
    @Test void nestedFieldDoesNotReplaceOuterField() {
        var unit=resolve("package p; class A { B value; class Inner { C value; } void f(){ value.run(); } } class B {void run(){}} class C {void run(){}}");
        assertThat(unit.invocations()).filteredOn(i -> i.targetMethodName().equals("run"))
                .extracting(InvocationReference::targetFqcn).containsExactly("p.B");
    }
    @Test void inheritedUniqueMethodUsesDeclaringClass() {
        var unit=resolve("package p; class Base {void run(){}} class Child extends Base {void f(){run();}}");
        assertThat(unit.invocations()).extracting(InvocationReference::targetFqcn).containsExactly("p.Base");
    }
    @Test void overloadedSignatureIsExplicitlyUnknown() {
        var unit=resolve("class A {void run(String s){} void run(Integer i){} void f(){run(null);}}");
        assertThat(unit.invocations()).extracting(InvocationReference::targetSignature).containsExactly("run(?)");
    }
    @Test void chainedReceiverIsCountedUnresolved() {
        var unit=resolve("class A { A make(){return this;} void f(){make().run();} void run(){} }");
        assertThat(unit.metrics().unresolvable()).isEqualTo(1);
    }
}
