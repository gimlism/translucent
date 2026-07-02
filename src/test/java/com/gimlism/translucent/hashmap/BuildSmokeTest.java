package com.gimlism.translucent.hashmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {
    @Test
    void toolchainRunsTestsOnJava21OrNewer() {
        // Compiled to Java 21 bytecode via maven.compiler.release=21; tests may
        // run on a newer JVM (e.g. JDK 26). Assert the runtime is 21+.
        assertTrue(Runtime.version().feature() >= 21);
    }
}
