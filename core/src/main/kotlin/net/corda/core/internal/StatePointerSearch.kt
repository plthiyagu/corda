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
        when {
            // StatePointer.
            field.type == LinearPointer::class.java -> statePointers.add(field.get(obj) as LinearPointer)
            field.type == StaticPointer::class.java -> statePointers.add(field.get(obj) as StaticPointer)
            // Not StatePointer.
            else -> {
                // Ignore classes which have not been loaded.
                // Assumption: all required state classes are already loaded.
                val packageName = field.type.`package`?.name ?: return

                // Ignore JDK classes.
                if (packageName.startsWith("java")) {
                    return
                }

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