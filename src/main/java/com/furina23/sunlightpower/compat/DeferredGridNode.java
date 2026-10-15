package com.furina23.sunlightpower.compat;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Keeps saved node data until a host is needed, and creates its grid node on the first world tick. */
final class DeferredGridNode<T, H extends DeferredGridNode.Host<T>> {
    interface Host<T> {
        void create();
        void destroy();
        void load(T data);
        T save();
    }

    private final Supplier<H> factory;
    private H host;
    private T savedData;
    private boolean scheduled;
    private boolean created;
    private boolean removed;

    DeferredGridNode(Supplier<H> factory) {
        this.factory = factory;
    }

    H getHost() {
        if (removed) throw new IllegalStateException("Cannot recreate a removed grid node");
        if (host == null) {
            host = factory.get();
            if (savedData != null) {
                host.load(savedData);
            }
        }
        return host;
    }

    void load(T data) {
        savedData = created ? null : data;
        if (host != null) host.load(data);
    }

    T save() {
        // ManagedGridNode.saveToNBT writes nothing until create(), even after loadFromNBT().
        return host == null || !created ? savedData : host.save();
    }

    void initialize(Consumer<Runnable> firstTick) {
        if (scheduled || removed) return;
        scheduled = true;
        firstTick.accept(() -> {
            if (!removed) {
                getHost().create();
                created = true;
                savedData = null;
            }
        });
    }

    void remove() {
        if (removed) return;
        removed = true;
        if (host != null) host.destroy();
        host = null;
        savedData = null;
    }
}
