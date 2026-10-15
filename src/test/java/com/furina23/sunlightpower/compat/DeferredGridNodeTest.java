package com.furina23.sunlightpower.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeferredGridNodeTest {
    @Test
    void savedNodeDataSurvivesLoadingBeforeAnyHostLookup() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        List<Runnable> firstTick = new ArrayList<>();

        // A newly loaded block entity reads NBT before it has a level or an AE2 host.
        lifecycle.load("saved-grid-id");
        assertEquals("saved-grid-id", lifecycle.save());
        assertFalse(host.created);

        lifecycle.initialize(firstTick::add);
        assertEquals(List.of(), host.events);
        firstTick.get(0).run();

        assertEquals(List.of("load:saved-grid-id", "create:saved-grid-id"), host.events);
        assertEquals("saved-grid-id", lifecycle.save());
    }

    @Test
    void lookupBeforeFirstTickLoadsDataWithoutCreatingTheGridEarly() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        lifecycle.load("saved-grid-id");

        assertSame(host, lifecycle.getHost());
        assertSame(host, lifecycle.getHost());
        assertEquals(List.of("load:saved-grid-id"), host.events);
        assertFalse(host.created);
        assertEquals("saved-grid-id", lifecycle.save());
    }

    @Test
    void loadAfterLevelAssignmentStillPrecedesGridCreation() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        List<Runnable> firstTick = new ArrayList<>();
        lifecycle.initialize(firstTick::add);
        lifecycle.getHost();
        lifecycle.load("saved-grid-id");
        firstTick.get(0).run();

        assertEquals(List.of("load:saved-grid-id", "create:saved-grid-id"), host.events);
    }

    @Test
    void repeatedInitializationCreatesOnlyOneGridNode() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        List<Runnable> firstTick = new ArrayList<>();
        lifecycle.initialize(firstTick::add);
        lifecycle.initialize(firstTick::add);
        assertEquals(1, firstTick.size());
        firstTick.get(0).run();
        lifecycle.initialize(firstTick::add);

        assertEquals(1, firstTick.size());
        assertEquals(List.of("create:null"), host.events);
    }

    @Test
    void removalBeforeFirstTickCancelsCreation() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        List<Runnable> firstTick = new ArrayList<>();
        lifecycle.load("saved-grid-id");
        lifecycle.initialize(firstTick::add);
        lifecycle.remove();
        firstTick.get(0).run();
        lifecycle.initialize(firstTick::add);

        assertEquals(List.of(), host.events);
        assertEquals(1, firstTick.size());
        assertNull(lifecycle.save());
        assertThrows(IllegalStateException.class, lifecycle::getHost);
    }

    @Test
    void removalDestroysAnExistingHostExactlyOnce() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        List<Runnable> firstTick = new ArrayList<>();
        lifecycle.getHost();
        lifecycle.initialize(firstTick::add);
        lifecycle.remove();
        lifecycle.remove();
        firstTick.get(0).run();

        assertEquals(List.of("destroy"), host.events);
    }

    @Test
    void saveAndReloadRestoresTheLatestNodeState() {
        FakeHost originalHost = new FakeHost();
        var original = new DeferredGridNode<>(() -> originalHost);
        original.load("old-grid-id");
        original.initialize(Runnable::run);
        originalHost.data = "current-grid-id";
        String saved = original.save();
        original.remove();

        FakeHost restoredHost = new FakeHost();
        var restored = new DeferredGridNode<>(() -> restoredHost);
        restored.load(saved);
        restored.initialize(Runnable::run);

        assertEquals(List.of("load:current-grid-id", "create:current-grid-id"), restoredHost.events);
        assertEquals("current-grid-id", restored.save());
        assertEquals(List.of("load:old-grid-id", "create:old-grid-id", "destroy"), originalHost.events);
    }

    @Test
    void reloadingAnActiveBlockEntityDelegatesToItsExistingManagedNode() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        lifecycle.load("old-grid-id");
        lifecycle.initialize(Runnable::run);

        // AE2 15.4.11 handles live NBT changes inside ManagedGridNode.loadFromNBT itself.
        lifecycle.load("new-grid-id");
        assertEquals("new-grid-id", lifecycle.save());
        List<Runnable> nextTick = new ArrayList<>();
        lifecycle.initialize(nextTick::add);

        assertEquals(0, nextTick.size());
        assertSame(host, lifecycle.getHost());
        assertEquals(List.of("load:old-grid-id", "create:old-grid-id", "load:new-grid-id"), host.events);
    }

    @Test
    void aNewPanelCanInitializeWithoutSavedNodeData() {
        FakeHost host = new FakeHost();
        var lifecycle = new DeferredGridNode<>(() -> host);
        assertNull(lifecycle.save());
        lifecycle.initialize(Runnable::run);
        assertEquals(List.of("create:null"), host.events);
    }

    private static final class FakeHost implements DeferredGridNode.Host<String> {
        private final List<String> events = new ArrayList<>();
        private String data;
        private boolean created;

        @Override
        public void create() {
            if (created) throw new IllegalStateException("Grid node created twice");
            created = true;
            events.add("create:" + data);
        }

        @Override
        public void destroy() {
            events.add("destroy");
        }

        @Override
        public void load(String data) {
            this.data = data;
            events.add("load:" + data);
        }

        @Override
        public String save() {
            return created ? data : null;
        }
    }
}
