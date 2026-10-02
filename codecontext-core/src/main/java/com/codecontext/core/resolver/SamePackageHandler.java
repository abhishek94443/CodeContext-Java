package com.codecontext.core.resolver;

import java.util.Optional;

public class SamePackageHandler implements ResolutionHandler {
    @Override
    public Optional<ResolutionResult> resolve(ResolutionContext context) {
        String target = context.targetName();
        String candidate = context.currentPackage().isBlank() ? target : context.currentPackage() + "." + target;
        if (context.symbolLookup().findByFqcn(candidate).isPresent()) {
            return Optional.of(new ResolutionResult(candidate, true, "SamePackageHandler"));
        }
        return Optional.empty();
    }
}