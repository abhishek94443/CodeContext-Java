package com.codecontext.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class StringFlyweightPoolTest {

    @Test
    @DisplayName("UTC-S1.1-US01-009 / FTC-S1.1-004: Deduplicates repeated strings and returns identical object references")
    void shouldInternIdenticalStringInstances() {
        StringFlyweightPool pool = new StringFlyweightPool();
        String str1 = new String("com.company.service");
        String str2 = new String("com.company.service");

        assertThat(str1).isNotSameAs(str2); // Pre-condition: two distinct heap objects

        String ref1 = pool.intern(str1);
        String ref2 = pool.intern(str2);

        assertThat(ref1).isSameAs(ref2);
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTC-S1.1-US01-010: Null input returns null without error")
    void shouldReturnNullWhenInputIsNull() {
        StringFlyweightPool pool = new StringFlyweightPool();

        assertThat(pool.intern(null)).isNull();
        assertThat(pool.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("UTC-S1.1-US01-011: Empty string handling returns canonical empty string")
    void shouldReturnCanonicalEmptyString() {
        StringFlyweightPool pool = new StringFlyweightPool();

        String ref = pool.intern("");
        assertThat(ref).isEqualTo("");
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTC-S1.1-US01-012: High concurrency stress testing with virtual threads")
    void shouldHandleConcurrentInterningSafely() throws Exception {
        StringFlyweightPool pool = new StringFlyweightPool();
        int threadCount = 30;
        int operationsPerThread = 200;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "package.name." + (j % 20); // 20 unique keys
                        String interned = pool.intern(new String(key));
                        assertThat(interned).isEqualTo(key);
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(); // Ensure no exception was thrown in any virtual thread
            }
        }

        assertThat(pool.size()).isEqualTo(20);
    }
}
