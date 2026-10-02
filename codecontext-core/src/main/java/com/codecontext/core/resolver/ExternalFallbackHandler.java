package com.codecontext.core.resolver;

import java.util.Optional;
import java.util.Set;

public class ExternalFallbackHandler implements ResolutionHandler {

    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "Byte", "Short", "Character", "CharSequence", "StringBuilder", "StringBuffer",
            "Thread", "Runnable", "System", "Math", "Exception", "RuntimeException",
            "Throwable", "Error", "IllegalArgumentException", "IllegalStateException",
            "NullPointerException", "IndexOutOfBoundsException", "UnsupportedOperationException"
    );

    @Override
    public Optional<ResolutionResult> resolve(ResolutionContext context) {
        String target = context.targetName();
        if (JAVA_LANG_TYPES.contains(target)) {
            return Optional.of(new ResolutionResult("java.lang." + target, false, "ExternalFallbackHandler"));
        }
        return Optional.of(new ResolutionResult(target, false, "ExternalFallbackHandler"));
    }
}