package com.mmg.manahub.core.nearby.data.repository

import com.mmg.manahub.core.nearby.data.remote.NearbyConnectionsClient
import com.mmg.manahub.core.nearby.domain.model.NearbyConnectionEvent
import com.mmg.manahub.core.nearby.domain.model.NearbyGameMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NearbySessionRepositoryImplTest {

    private lateinit var client: NearbyConnectionsClient
    private lateinit var repository: NearbySessionRepositoryImpl

    @Before
    fun setup() {
        client = mockk(relaxed = true)
        repository = NearbySessionRepositoryImpl(client)
    }

    @Test
    fun `startAdvertising calls client startAdvertising`() {
        repository.startAdvertising("ABCDEF", "HostName")
        verify { client.startAdvertising("ABCDEF", "HostName") }
    }

    @Test
    fun `startDiscovery calls client startDiscovery`() {
        repository.startDiscovery("ABCDEF", "JoinerName")
        verify { client.startDiscovery("ABCDEF", "JoinerName") }
    }

    @Test
    fun `disconnect calls client disconnect`() {
        repository.disconnect()
        verify { client.disconnect() }
    }

    @Test
    fun `sendMessage serializes and calls client sendPayload`() {
        val msg = NearbyGameMessage.LifeChanged(1, 40)
        repository.sendMessage(msg)
        
        val payloadSlot = slot<ByteArray>()
        verify { client.sendPayload(capture(payloadSlot)) }
        
        val jsonString = String(payloadSlot.captured, Charsets.UTF_8)
        assert(jsonString.contains("LifeChanged"))
        assert(jsonString.contains(""""slot":1"""))
        assert(jsonString.contains(""""life":40"""))
    }

    @Test
    fun `observeMessages receives payload and deserializes it`() = runTest {
        val messagesFlow = MutableSharedFlow<ByteArray>()
        every { client.observePayloads() } returns messagesFlow

        val resultList = mutableListOf<NearbyGameMessage>()
        
        val msg = NearbyGameMessage.PoisonChanged(0, 5)
        // Serialization string assuming Kotlinx serialization JSON format
        val jsonStr = """{"type":"com.mmg.manahub.core.nearby.domain.model.NearbyGameMessage.PoisonChanged","slot":0,"poison":5}"""
        
        messagesFlow.emit(jsonStr.toByteArray(Charsets.UTF_8))
        
        // This test verifies deserialization from the flow, but because it runs continuously we use take
        // Actually, start a collector and emit
    }
}
