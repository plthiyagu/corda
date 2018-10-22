package net.corda.djvm

import net.corda.djvm.assertions.AssertionExtensions.assertThatDJVM
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import sandbox.SandboxFunction
import sandbox.Task
import java.util.*

class DJVMExceptionTest : TestBase() {
    @Test
    fun testSingleException() = parentedSandbox {
        val result = Task(SingleExceptionTask()).apply("Hello World")
        assertThat(result).isInstanceOf(Throwable::class.java)
        result as Throwable

        assertThat(result.message).isEqualTo("Hello World")
        assertThat(result.cause).isNull()
        assertThat(result.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
    }

    @Test
    fun testMultipleExceptions() = parentedSandbox {
        val result = Task(MultipleExceptionsTask()).apply("Hello World")
        assertThat(result).isInstanceOf(Throwable::class.java)
        result as Throwable

        assertThat(result.message).isEqualTo("Hello World(1)(2)")
        assertThat(result.cause).isInstanceOf(Throwable::class.java)
        assertThat(result.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
        val resultLineNumbers = result.stackTrace.toLineNumbers()

        val firstCause = result.cause as Throwable
        assertThat(firstCause.message).isEqualTo("Hello World(1)")
        assertThat(firstCause.cause).isInstanceOf(Throwable::class.java)
        assertThat(firstCause.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
        val firstCauseLineNumbers = firstCause.stackTrace.toLineNumbers()

        val rootCause = firstCause.cause as Throwable
        assertThat(rootCause.message).isEqualTo("Hello World")
        assertThat(rootCause.cause).isNull()
        assertThat(rootCause.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
        val rootCauseLineNumbers = rootCause.stackTrace.toLineNumbers()

        // These stack traces should share one line number and have one distinct line number each.
        assertThat(resultLineNumbers.toSet() + firstCauseLineNumbers.toSet() + rootCauseLineNumbers.toSet())
            .hasSize(4)
    }

    @Test
    fun testJavaThrowableToSandbox() = parentedSandbox {
        val djvm = DJVM(classLoader)
        val helloWorld = djvm.stringOf("Hello World")

        val result = djvm.sandbox(Throwable("Hello World"))
        assertThatDJVM(result)
            .hasClassName("sandbox.java.lang.Throwable")
            .isAssignableFrom(djvm.throwable)
            .hasGetterValue("getMessage", helloWorld)
            .hasGetterNullValue("getCause")

        assertThat(result.getArray("getStackTrace"))
            .hasOnlyElementsOfType(djvm.stackTraceElement)
            .isNotEmpty()
    }

    @Test
    fun testWeCreateCorrectJVMExceptionAtRuntime() = parentedSandbox {
        val djvm = DJVM(classLoader)
        val helloWorld = djvm.stringOf("Hello World")

        val result = djvm.sandbox(RuntimeException("Hello World"))
        assertThatDJVM(result)
            .hasClassName("sandbox.java.lang.RuntimeException")
            .isAssignableFrom(djvm.throwable)
            .hasGetterValue("getMessage", helloWorld)
            .hasGetterNullValue("getCause")

        assertThat(result.getArray("getStackTrace"))
            .hasOnlyElementsOfType(djvm.stackTraceElement)
            .isNotEmpty()

        assertThatExceptionOfType(ClassNotFoundException::class.java)
            .isThrownBy { djvm.classFor("sandbox.java.lang.RuntimeException\$1DJVM") }
            .withMessage("sandbox.java.lang.RuntimeException\$1DJVM")
    }

    @Test
    fun testWeCreateCorrectSyntheticExceptionAtRuntime() = parentedSandbox {
        val djvm = DJVM(classLoader)

        val result = djvm.sandbox(EmptyStackException())
        assertThatDJVM(result)
            .hasClassName("sandbox.java.util.EmptyStackException")
            .isAssignableFrom(djvm.throwable)
            .hasGetterNullValue("getMessage")
            .hasGetterNullValue("getCause")

        assertThat(result.getArray("getStackTrace"))
            .hasOnlyElementsOfType(djvm.stackTraceElement)
            .isNotEmpty()

        assertThatDJVM(djvm.classFor("sandbox.java.util.EmptyStackException\$1DJVM"))
            .isAssignableFrom(RuntimeException::class.java)
    }

    @Test
    fun testWeCannotCreateSyntheticExceptionForNonException() = parentedSandbox {
        val djvm = DJVM(classLoader)
        assertThatExceptionOfType(ClassNotFoundException::class.java)
            .isThrownBy { djvm.classFor("sandbox.java.util.LinkedList\$1DJVM") }
            .withMessage("sandbox.java.util.LinkedList\$1DJVM")
    }
}

class SingleExceptionTask : SandboxFunction<Any?, sandbox.java.lang.Throwable> {
    override fun apply(input: Any?): sandbox.java.lang.Throwable? {
        return sandbox.java.lang.Throwable(input as? sandbox.java.lang.String)
    }
}

class MultipleExceptionsTask : SandboxFunction<Any?, sandbox.java.lang.Throwable> {
    override fun apply(input: Any?): sandbox.java.lang.Throwable? {
        val root = sandbox.java.lang.Throwable(input as? sandbox.java.lang.String)
        val nested = sandbox.java.lang.Throwable(root.message + "(1)", root)
        return sandbox.java.lang.Throwable(nested.message + "(2)", nested)
    }
}

private infix operator fun sandbox.java.lang.String.plus(s: String): sandbox.java.lang.String {
    return (toString() + s).toDJVM()
}

private fun Array<StackTraceElement>.toLineNumbers(): IntArray {
    return map(StackTraceElement::getLineNumber).toIntArray()
}