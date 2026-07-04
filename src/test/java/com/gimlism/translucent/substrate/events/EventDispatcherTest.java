package com.gimlism.translucent.substrate.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventDispatcherTest {
    private record TestSnap() implements StructureSnapshot {}

    private record TestEvent(StructureSnapshot after) implements StructureEvent {}

    private static TestEvent event() {
        return new TestEvent(new TestSnap());
    }

    @Test
    void reentrancyGuardRejectsNestedBeginThenResets() {
        var d = new EventDispatcher<TestEvent>("thing");
        d.beginMutation();
        assertThrows(ConcurrentModificationException.class, d::beginMutation);
        d.endMutation();
        assertDoesNotThrow(d::beginMutation); // guard resets
    }

    @Test
    void addRemoveAndEmitDeliverInOrder() {
        var d = new EventDispatcher<TestEvent>("thing");
        var received = new ArrayList<TestEvent>();
        StructureEventListener<TestEvent> l = received::add;
        d.addListener(l);
        var first = event();
        d.emit(first);
        d.removeListener(l);
        d.emit(event()); // no longer delivered
        assertEquals(List.of(first), received);
    }

    @Test
    void emitCopiesListenerListSoAListenerMayRegisterDuringDispatch() {
        var d = new EventDispatcher<TestEvent>("thing");
        d.addListener(e -> d.addListener(x -> { })); // mutate the listener list during dispatch
        assertDoesNotThrow(() -> d.emit(event()));   // copy-on-dispatch: no ConcurrentModificationException
    }
}
