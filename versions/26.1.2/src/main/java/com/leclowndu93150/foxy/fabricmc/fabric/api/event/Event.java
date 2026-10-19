package com.leclowndu93150.foxy.fabricmc.fabric.api.event;

/**
 * Fabric API event: listeners register callbacks, the owner calls {@link #invoker()} to fire them.
 * Created through {@link EventFactory#createArrayBacked}.
 */
public abstract class Event<T> {
    protected volatile T invoker;

    public final T invoker() {
        return invoker;
    }

    public abstract void register(T listener);
}
