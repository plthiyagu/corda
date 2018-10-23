package net.corda.core.internal

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.StatePointer
import net.corda.core.contracts.StaticPointer
import net.corda.core.schemas.CommonSchemaV1
import java.lang.reflect.Field
import java.util.*

/**
 * Uses reflection to search for instances of [StatePointer] within a [ContractState].
 */
class StatePointerSearch(val state: ContractState) {

    // Type required for traversal.
    private data class FieldWithObject(val obj: Any, val field: Field)

    // List containing all discovered state pointers.
    private val statePointers = mutableSetOf<StatePointer>()

    // Record seen fields to avoid getting stuck in loops.
    private val seenObjects = mutableSetOf<Any>().apply { add(state) }

    // Queue of fields to search.
    private val fieldQueue = ArrayDeque<FieldWithObject>().apply { addAllFields(state) }

    // Helper for adding all fields to the queue.
    private fun ArrayDeque<FieldWithObject>.addAllFields(obj: Any) {
        val fieldsWithObjects = obj::class.java.declaredFields.map { FieldWithObject(obj, it) }
        addAll(fieldsWithObjects)
    }

    private fun handleField(obj: Any, field: Field) {
        // TODO: There are probably other edge cases, security issues which need handling.
        println("package: " + field.type.`package`.name)
        when {
            field.type == LinearPointer::class.java -> statePointers.add(field.get(obj) as LinearPointer)
            field.type == StaticPointer::class.java -> statePointers.add(field.get(obj) as StaticPointer)
            // Ignore classes which have not been loaded.
            // Assumption is that all required state classes are already loaded.
            field.type.`package` == null -> return
            // Ignore JDK classes.
            field.type.`package`.name.startsWith("java") -> return
            else -> {
                // Ignore nulls.
                val newObj = field.get(obj) ?: return
                fieldQueue.addAllFields(newObj)
                seenObjects.add(obj)
            }
        }
    }

    fun search(): Set<StatePointer> {
        while (fieldQueue.isNotEmpty()) {
            val (obj, field) = fieldQueue.pop()
            // Ignore seen fields.
            if (field in seenObjects) {
                continue
            } else {
                field.isAccessible = true
                handleField(obj, field)
            }
        }
        return statePointers
    }
}