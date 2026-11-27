// Minimal test-only assertion API for executing the existing pure Kotlin tests
// without Gradle/JUnit. The actual Gradle task must still run with real JUnit.
package org.junit

annotation class Test
object Assert {
    fun assertEquals(expected: Any?, actual: Any?) {
        check(expected == actual) { "Values are not equal" }
    }
    fun assertFalse(actual: Boolean) { check(!actual) }
    fun assertTrue(actual: Boolean) { check(actual) }
}
