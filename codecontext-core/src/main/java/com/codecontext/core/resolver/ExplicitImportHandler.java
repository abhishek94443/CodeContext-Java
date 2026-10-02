package com.codecontext.core.resolver;

import java.util.Optional;

public class ExplicitImportHandler implements ResolutionHandler {
    @Override
    public Optional<ResolutionResult> resolve(ResolutionContext context) {
        String target = context.targetName();
        for (String imp : context.explicitImports()) {
            if (imp.endsWith("." + target)) {
                boolean local = context.symbolLookup().findByFqcn(imp).isPresent();
                return Optional.of(new ResolutionResult(imp, local, "ExplicitImportHandler"));
            }
        }
        return Optional.empty();
    }
}