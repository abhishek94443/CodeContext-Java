package com.codecontext.core.scope;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexicalScopeStackTest {

    private LexicalScopeStack scopeStack;

    @BeforeEach
    void setUp() {
        scopeStack = new LexicalScopeStack();
    }

    @Test
    @DisplayName("UTC-S1.2-US02-011: Push and pop frame updates depth")
    void testPushAndPopFrame() {
        assertThat(scopeStack.depth()).isEqualTo(1); // Initial root frame
        scopeStack.push();
        assertThat(scopeStack.depth()).isEqualTo(2);
        scopeStack.pop();
        assertThat(scopeStack.depth()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTC-S1.2-US02-012: Declare and lookup variable in current frame")
    void testDeclareAndLookupCurrentFrame() {
        scopeStack.declare("count", "int");
        Optional<String> type = scopeStack.lookup("count");
        assertThat(type).contains("int");
    }

    @Test
    @DisplayName("UTC-S1.2-US02-013: Lookup variable in parent frame when not in inner frame")
    void testLookupParentFrame() {
        scopeStack.declare("name", "String");
        scopeStack.push();
        scopeStack.declare("localVal", "double");

        assertThat(scopeStack.lookup("name")).contains("String");
        assertThat(scopeStack.lookup("localVal")).contains("double");
    }

    @Test
    @DisplayName("UTC-S1.2-US02-014: Variable shadowing in child frame takes precedence")
    void testVariableShadowing() {
        scopeStack.declare("x", "ParentType");
        scopeStack.push();
        scopeStack.declare("x", "ChildType");

        assertThat(scopeStack.lookup("x")).contains("ChildType");
    }

    @Test
    @DisplayName("UTC-S1.2-US02-015: Pop frame restores outer variable visibility")
    void testPopRestoresOuterVariable() {
        scopeStack.declare("x", "ParentType");
        scopeStack.push();
        scopeStack.declare("x", "ChildType");
        assertThat(scopeStack.lookup("x")).contains("ChildType");

        scopeStack.pop();
        assertThat(scopeStack.lookup("x")).contains("ParentType");
    }

    @Test
    @DisplayName("UTC-S1.2-US02-016: Lookup undeclared variable returns Optional.empty")
    void testLookupUndeclaredVariable() {
        assertThat(scopeStack.lookup("nonExistent")).isEmpty();
    }

    @Test
    @DisplayName("UTC-S1.2-US02-017: Pop when only root frame exists throws IllegalStateException")
    void testPopRootFrameThrowsException() {
        assertThat(scopeStack.depth()).isEqualTo(1);
        assertThatThrownBy(() -> scopeStack.pop())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot pop the root scope frame");
    }

    @Test
    @DisplayName("UTC-S1.2-US02-018: Re-declaring variable in same frame updates type")
    void testRedeclareSameFrame() {
        scopeStack.declare("varName", "TypeA");
        assertThat(scopeStack.lookup("varName")).contains("TypeA");

        scopeStack.declare("varName", "TypeB");
        assertThat(scopeStack.lookup("varName")).contains("TypeB");
    }
}