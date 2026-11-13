package com.codecontext.core.security.rule;

import com.codecontext.core.finding.FindingCategory;
import com.codecontext.core.finding.FindingSeverity;
import com.codecontext.core.finding.RuleMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityRuleRegistryTest {

    @Test
    @DisplayName("SecurityRuleRegistry registers, retrieves, and clears rules")
    void testRegistryLifecycle() {
        SecurityRuleRegistry registry = SecurityRuleRegistry.empty();
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.size()).isEqualTo(0);

        SecurityRule rule1 = new DemonstrationSecurityRule();
        registry.register(rule1);

        assertThat(registry.isEmpty()).isFalse();
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getRule(DemonstrationSecurityRule.RULE_ID)).contains(rule1);
        assertThat(registry.allRules()).containsExactly(rule1);

        registry.clear();
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.getRule(DemonstrationSecurityRule.RULE_ID)).isEmpty();
    }

    @Test
    @DisplayName("SecurityRuleRegistry rejects null rule registration")
    void testNullRuleRejection() {
        SecurityRuleRegistry registry = new SecurityRuleRegistry();
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class);
    }
}

