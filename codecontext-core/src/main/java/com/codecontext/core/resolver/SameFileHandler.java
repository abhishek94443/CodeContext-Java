package com.codecontext.core.resolver;

import java.util.Optional;

public class SameFileHandler implements ResolutionHandler {
    @Override
    public Optional<ResolutionResult> resolve(ResolutionContext context) {
        String target = context.targetName();
        for (String fqcn : context.sameFileTypes()) {
            if (fqcn.equals(target) || fqcn.endsWith("." + target) || fqcn.endsWith("$" + target)) {
                return Optional.of(new ResolutionResult(fqcn, true, "SameFileHandler"));
            }
        }
        return Optional.empty();
    }
}