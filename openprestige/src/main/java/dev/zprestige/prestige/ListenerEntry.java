package dev.zprestige.prestige;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

public class ListenerEntry {
    private final Method method;
    private final Object target;
    private final Class eventType;
    private final MethodHandle handle;

    public ListenerEntry(Method subscriberMethod, Object subscriber, Class subscribedType) {
        this(subscriberMethod, subscriber, subscribedType, null);
    }

    public ListenerEntry(Method subscriberMethod, Object subscriber, Class subscribedType, MethodHandle boundHandle) {
        this.method = subscriberMethod;
        this.target = subscriber;
        this.eventType = subscribedType;
        this.handle = boundHandle;
    }

    public Method getMethod() {
        return this.method;
    }

    public Object getTarget() {
        return this.target;
    }

    public Class getEventType() {
        return this.eventType;
    }

    public MethodHandle getHandle() {
        return this.handle;
    }
}
