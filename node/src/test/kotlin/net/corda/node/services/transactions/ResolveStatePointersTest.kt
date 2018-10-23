package net.corda.node.services.transactions

import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class ResolveStatePointersTest {

    private data class Bar(override val participants: List<AbstractParty>, override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState
    private data class Foo(val baz: LinearPointer, override val participants: List<AbstractParty>) : ContractState

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val mockIdentityService = rigorousMock<IdentityServiceInternal>().also {
        Mockito.doReturn(myself.party).whenever(it).partyFromKey(myself.publicKey)
        Mockito.doReturn(notary.party).whenever(it).partyFromKey(notary.publicKey)
        Mockito.doReturn(myself.party).whenever(it).wellKnownPartyFromAnonymous(myself.party)
        Mockito.doReturn(notary.party).whenever(it).wellKnownPartyFromAnonymous(notary.party)
        Mockito.doReturn(myself.party).whenever(it).wellKnownPartyFromX500Name(myself.name)
        Mockito.doReturn(notary.party).whenever(it).wellKnownPartyFromX500Name(notary.name)
    }

    val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    val notary = TestIdentity(DUMMY_NOTARY_NAME, 20)
    val cordapps = listOf("net.corda.testing.contracts")
    val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordapps,
            mockIdentityService,
            myself
    )

    val database = databaseAndServices.first
    val services = databaseAndServices.second

    @Test
    fun `test`() {
        // Create the pointed to state.
        val linearId = services.run {
            val tx = signInitialTransaction(TransactionBuilder(notary = notary.party).apply {
                addOutputState(Bar(listOf(myself.party)), DummyContract.PROGRAM_ID)
                addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
            })
            recordTransactions(listOf(tx))
            tx.tx.outputsOfType<LinearState>().single().linearId
        }

        val tx = TransactionBuilder(notary = notary.party).apply {
            val pointer = LinearPointer(linearId)
            addOutputState(Foo(pointer, listOf(myself.party)), DummyContract.PROGRAM_ID, resolveStatePointers = true)
            addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
        }

        println(tx)

    }

}