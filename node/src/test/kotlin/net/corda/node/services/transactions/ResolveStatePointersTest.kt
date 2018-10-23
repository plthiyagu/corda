package net.corda.node.services.transactions

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ResolveStatePointersTest {

    private data class Bar(override val participants: List<AbstractParty>, override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState
    private data class Foo(val baz: LinearPointer, override val participants: List<AbstractParty>) : ContractState

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    private val notary = TestIdentity(DUMMY_NOTARY_NAME, 20)
    private val cordapps = listOf("net.corda.testing.contracts")
    private val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = cordapps,
            identityService = makeTestIdentityService(notary.identity, myself.identity),
            initialIdentity = myself,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    private val database = databaseAndServices.first
    private val services = databaseAndServices.second

    @Test
    fun `resolve state pointers and check reference state is added to transaction`() {
        val bar = Bar(listOf(myself.party))

        // Create the pointed to state.
        val stateAndRef = services.run {
            val tx = signInitialTransaction(TransactionBuilder(notary = notary.party).apply {
                addOutputState(bar, DummyContract.PROGRAM_ID)
                addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
            })
            recordTransactions(listOf(tx))
            tx.tx.outRefsOfType<LinearState>().single()
        }

        val linearId = stateAndRef.state.data.linearId

        // Add a new state containing a linear pointer.
        val tx = TransactionBuilder(notary = notary.party).apply {
            val pointer = LinearPointer(linearId)
            addOutputState(Foo(pointer, listOf(myself.party)), DummyContract.PROGRAM_ID)
            addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
            resolveStatePointers(services)
        }

        // Check the StateRef for the pointed-to state is added as a reference.
        assertEquals(stateAndRef.ref, tx.referenceStates().single())

        // Resolve the StateRef to the actual state.
        val ltx = tx.toLedgerTransaction(services)
        assertEquals(bar, ltx.referenceStates.single())
    }

}