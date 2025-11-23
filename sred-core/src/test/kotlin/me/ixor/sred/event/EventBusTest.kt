package me.ixor.sred.event

import me.ixor.sred.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.*
import java.time.Instant

class EventBusTest {
    
    @Test
    fun `test event bus creation`() {
        val eventBus = EventBusFactory.create()
        assertNotNull(eventBus)
    }
    
    @Test
    fun `test event bus start and stop`() = runBlocking {
        val eventBus = EventBusFactory.create()
        
        eventBus.start()
        assertTrue(true) // If no exception, start succeeded
        
        eventBus.stop()
        assertTrue(true) // If no exception, stop succeeded
    }
    
    @Test
    fun `test event subscription and publishing`() = runBlocking {
        val eventBus = EventBusFactory.create()
        eventBus.start()
        
        var receivedEvent: Event? = null
        val listener = object : EventListener {
            override val id: String = "test_listener"
            
            override suspend fun onEvent(event: Event) {
                receivedEvent = event
            }
            
            override suspend fun onError(event: Event, error: Throwable) {
                // Ignore errors in test
            }
        }
        
        val eventType = EventType("test", "test_event")
        val subscription = eventBus.subscribe(eventType, listener)
        
        assertNotNull(subscription)
        assertEquals(eventType, subscription.eventType)
        assertEquals(listener, subscription.listener)
        
        val event = EventFactory.create(
            type = eventType,
            name = "Test Event",
            description = "A test event"
        )
        
        eventBus.publish(event)
        
        // Wait a bit for async processing with timeout
        withTimeout(2000) {
            while (receivedEvent == null) {
                delay(50)
            }
        }
        
        assertNotNull(receivedEvent)
        assertEquals(event.id, receivedEvent!!.id)
        assertEquals(event.type, receivedEvent!!.type)
        
        eventBus.stop()
    }
    
    @Test
    fun `test event bus statistics`() = runBlocking {
        val eventBus = EventBusFactory.create()
        eventBus.start()
        
        val listener = object : EventListener {
            override val id: String = "test_listener"
            
            override suspend fun onEvent(event: Event) {
                // Do nothing
            }
            
            override suspend fun onError(event: Event, error: Throwable) {
                // Ignore errors in test
            }
        }
        
        val eventType = EventType("test", "test_event")
        eventBus.subscribe(eventType, listener)
        
        val event = EventFactory.create(
            type = eventType,
            name = "Test Event",
            description = "A test event"
        )
        
        eventBus.publish(event)
        
        // Wait a bit for async processing with timeout
        withTimeout(2000) {
            delay(100)
        }
        
        val stats = eventBus.getStatistics()
        assertTrue(stats.totalEventsPublished > 0)
        assertTrue(stats.totalEventsProcessed > 0)
        
        eventBus.stop()
    }
    
    @Test
    fun `test event bus unsubscribe`() = runBlocking {
        val eventBus = EventBusFactory.create()
        eventBus.start()
        
        var receivedEvent: Event? = null
        val listener = object : EventListener {
            override val id: String = "test_listener"
            
            override suspend fun onEvent(event: Event) {
                receivedEvent = event
            }
            
            override suspend fun onError(event: Event, error: Throwable) {
                // Ignore errors in test
            }
        }
        
        val eventType = EventType("test", "test_event")
        val subscription = eventBus.subscribe(eventType, listener)
        
        // Unsubscribe
        eventBus.unsubscribe(subscription)
        
        val event = EventFactory.create(
            type = eventType,
            name = "Test Event",
            description = "A test event"
        )
        
        eventBus.publish(event)
        
        // Wait a bit for async processing
        delay(100)
        
        // Should not receive event after unsubscribe
        assertNull(receivedEvent)
        
        eventBus.stop()
    }
}
