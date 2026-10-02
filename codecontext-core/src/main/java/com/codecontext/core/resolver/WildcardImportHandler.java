package com.codecontext.core.resolver;

import java.util.Optional;

public class WildcardImportHandler implements ResolutionHandler {
    @Override
    public Optional<ResolutionResult> resolve(ResolutionContext context) {
        String target = context.targetName();
        for (String imp : context.wildcardImports()) {
            if (imp.endsWith(".*")) {
                String pkg = imp.substring(0, imp.length() - 2);
                String candidate = pkg + "." + target;
                if (context.symbolLookup().findByFqcn(candidate).isPresent()) {
                    return Optional.of(new ResolutionResult(candidate, true, "WildcardImportHandler"));
                }
            }
        }
        return Optional.empty();
    }
}