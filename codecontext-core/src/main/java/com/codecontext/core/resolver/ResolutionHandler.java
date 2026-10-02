package com.codecontext.core.resolver;

import java.util.Optional;

/**
 * Strategy interface for individual resolution steps in the Chain of Responsibility.
 */
@FunctionalInterface
public interface ResolutionHandler {
    Optional<ResolutionResult> resolve(ResolutionContext context);
}