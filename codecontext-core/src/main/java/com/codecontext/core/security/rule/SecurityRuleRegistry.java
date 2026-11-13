package com.codecontext.core.security.rule;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry maintaining the active set of security and cryptographic analysis rules.
 */
public class SecurityRuleRegistry {

    private final Map<String, SecurityRule> rules = new ConcurrentHashMap<>();

    public void register(SecurityRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        rules.put(rule.id(), rule);
    }

    public Optional<SecurityRule> getRule(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rules.get(id));
    }

    public Collection<SecurityRule> allRules() {
        return Collections.unmodifiableCollection(rules.values());
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public int size() {
        return rules.size();
    }

    public void clear() {
        rules.clear();
    }

    /**
     * Creates an empty registry (no rules active).
     */
    public static SecurityRuleRegistry empty() {
        return new SecurityRuleRegistry();
    }

    /**
     * Creates a default registry populated with the standard cryptographic security rules.
     */
    public static SecurityRuleRegistry defaultRegistry() {
        SecurityRuleRegistry registry = new SecurityRuleRegistry();
        registry.register(new EcbModeSecurityRule());
        registry.register(new LegacyCipherSecurityRule());
        registry.register(new WeakDigestSecurityRule());
        registry.register(new ObsoleteTlsSecurityRule());
        registry.register(new ProviderDependentCipherSecurityRule());
        return registry;
    }
}

