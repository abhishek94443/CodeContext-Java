package com.codecontext.core.scope;

import java.util.*;

/**
 * Thread-confined Lexical Scope Stack.
 * Tracks variable and parameter type mappings across nested code blocks (methods, loops, if, try/catch).
 * Provides outward lexical shadowing resolution.
 */
public class LexicalScopeStack {

    private final Deque<Map<String, String>> frames = new ArrayDeque<>();

    public LexicalScopeStack() {
        // Always initialize with one root frame
        frames.push(new HashMap<>());
    }

    /**
     * Push a new child lexical scope frame onto the stack.
     */
    public void push() {
        frames.push(new HashMap<>());
    }

    /**
     * Pop the current lexical scope frame from the stack.
     * @throws IllegalStateException if attempting to pop the root frame or an empty stack.
     */
    public void pop() {
        if (frames.size() <= 1) {
            throw new IllegalStateException("Cannot pop the root scope frame");
        }
        frames.pop();
    }

    /**
     * Declare a variable and its associated declared type in the innermost scope frame.
     */
    public void declare(String name, String type) {
        if (name == null || type == null) {
            return;
        }
        Map<String, String> currentFrame = frames.peek();
        if (currentFrame != null) {
            currentFrame.put(name, type);
        }
    }

    /**
     * Look up a variable by name starting from the innermost scope frame
     * outward to the root frame (lexical shadowing order).
     *
     * @return Optional containing the declared type if found, or empty.
     */
    public Optional<String> lookup(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (Map<String, String> frame : frames) {
            String type = frame.get(name);
            if (type != null) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the current depth of the scope stack.
     */
    public int depth() {
        return frames.size();
    }

    /**
     * Reset the stack back to a single empty root frame.
     */
    public void reset() {
        frames.clear();
        frames.push(new HashMap<>());
    }
}