package com.codecontext.core.resolver;

import java.util.*;

/**
 * Orchestrates the 6-step Chain of Responsibility for type and invocation resolution.
 * Enforces strict JLS resolution precedence:
 * 1. Same File (including inner/nested classes)
 * 2. Same Package (takes precedence over wildcard imports per JLS)
 * 3. Explicit Single-Type Imports
 * 4. Wildcard Package Imports
 * 5. External Fallback (java.lang and unresolvable 3rd party types)
 */
public class ResolutionChain {

    private final List<ResolutionHandler> handlers;

    public ResolutionChain(List<ResolutionHandler> handlers) {
        this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers cannot be null"));
    }

    /**
     * Construct the standard production Chain of Responsibility.
     */
    public static ResolutionChain standardChain() {
        return new ResolutionChain(List.of(
                new SameFileHandler(),
                new SamePackageHandler(),
                new ExplicitImportHandler(),
                new WildcardImportHandler(),
                new ExternalFallbackHandler()
        ));
    }

    /**
     * Resolve the target name through the chain.
     */
    public ResolutionResult resolve(ResolutionContext context) {
        for (ResolutionHandler handler : handlers) {
            Optional<ResolutionResult> result = handler.resolve(context);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return new ResolutionResult(context.targetName(), false, "DefaultFallback");
    }
}