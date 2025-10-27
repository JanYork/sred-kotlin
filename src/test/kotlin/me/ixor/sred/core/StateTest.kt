package me.ixor.sred.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.runBlocking
import java.time.Instant

class StateTest {
    
    @Test
    fun `test state creation and basic properties`() {
        val state = TestState("test_state", "Test State", "A test state")
        
        assertEquals("test_state", state.id)
        assertEquals("Test State", state.name)
        assertEquals("A test state", state.description)
    }
    
    @Test
    fun `test state can enter`() {
        val state = TestState("test_state", "Test State", "A test state")
        val context = StateContextFactory.create()
        
        assertTrue(state.canEnter(context))
    }
    
    @Test
    fun `test state on enter`() = runBlocking {
        val state = TestState("test_state", "Test State", "A test state")
        val context = StateContextFactory.create()
        
        val newContext = state.onEnter(context)
        assertEquals(context, newContext)
    }
    
    @Test
    fun `test state on exit`() = runBlocking {
        val state = TestState("test_state", "Test State", "A test state")
        val context = StateContextFactory.create()
        
        val newContext = state.onExit(context)
        assertEquals(context, newContext)
    }
    
    @Test
    fun `test state can handle event`() {
        val state = TestState("test_state", "Test State", "A test state")
        val context = StateContextFactory.create()
        val event = EventFactory.create(
            type = EventType("test", "test_event"),
            name = "Test Event",
            description = "A test event"
        )
        
        assertFalse(state.canHandle(event, context))
    }
    
    @Test
    fun `test state handle event`() = runBlocking {
        val state = TestState("test_state", "Test State", "A test state")
        val context = StateContextFactory.create()
        val event = EventFactory.create(
            type = EventType("test", "test_event"),
            name = "Test Event",
            description = "A test event"
        )
        
        val result = state.handleEvent(event, context)
        assertFalse(result.success)
        assertNotNull(result.error)
    }
    
    @Test
    fun `test state get possible transitions`() {
        val state = TestState("test_state", "Test State", "A test state")
        val context = StateContextFactory.create()
        
        val transitions = state.getPossibleTransitions(context)
        assertTrue(transitions.isEmpty())
    }
}

class TestState(
    id: StateId,
    name: String,
    description: String
) : AbstractState(id, name, description)
