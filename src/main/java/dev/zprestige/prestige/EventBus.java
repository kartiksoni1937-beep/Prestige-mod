package dev.zprestige.prestige;

import dev.zprestige.prestige.event.Event;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private volatile Map listeners = new HashMap();
    private final Object lock = new Object();
    private static final MethodType LISTENER_TYPE;
    private static final Set LOGGED_FAILURES;
    public final CopyOnWriteArrayList registrationBlockers = new CopyOnWriteArrayList();
    private static final String LISTENER_ERROR_PREFIX = "[Prestige/EventBus] listener threw (logged once per type): ";

    public void register(Object subscriber) {
        if (subscriber != null) {
            if (this.registrationBlockers.isEmpty()) {
                Class subscriberClass = subscriber.getClass();
                ArrayList found = new ArrayList();

                for (Method method : subscriberClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Subscribe.class)) {
                        Class[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 1) {
                            try {
                                method.setAccessible(true);
                                MethodHandle handle = MethodHandles.lookup().unreflect(method).bindTo(subscriber).asType(LISTENER_TYPE);
                                found.add(new ListenerEntry(method, subscriber, paramTypes[0], handle));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }

                if (!found.isEmpty()) {
                    synchronized (this.lock) {
                        if (!isRegistered(this.listeners, subscriber)) {
                            HashMap updated = new HashMap(this.listeners);

                            for (ListenerEntry entry : (Iterable<ListenerEntry>)(found)) {
                                ListenerEntry[] existing = (ListenerEntry[])updated.get(entry.getEventType());
                                ListenerEntry[] merged;
                                if (existing == null) {
                                    merged = new ListenerEntry[]{entry};
                                } else {
                                    merged = new ListenerEntry[existing.length + 1];
                                    System.arraycopy(existing, 0, merged, 0, existing.length);
                                    merged[existing.length] = entry;
                                }

                                updated.put(entry.getEventType(), merged);
                            }

                            this.listeners = updated;
                        }
                    }
                }
            }
        }
    }

    public void unregister(Object subscriber) {
        if (subscriber != null) {
            synchronized (this.lock) {
                HashMap updated = new HashMap(this.listeners);
                boolean changed = false;

                for (Entry bucket : (Iterable<Entry>)(new ArrayList(updated.entrySet()))) {
                    ListenerEntry[] entries = (ListenerEntry[])bucket.getValue();
                    int remaining = 0;

                    for (ListenerEntry entry : entries) {
                        if (entry.getTarget() != subscriber) {
                            remaining++;
                        }
                    }

                    if (remaining != entries.length) {
                        changed = true;
                        if (remaining == 0) {
                            updated.remove(bucket.getKey());
                        } else {
                            ListenerEntry[] kept = new ListenerEntry[remaining];
                            int index = 0;

                            for (ListenerEntry candidate : entries) {
                                if (candidate.getTarget() != subscriber) {
                                    kept[index++] = candidate;
                                }
                            }

                            updated.put((Class)bucket.getKey(), kept);
                        }
                    }
                }

                if (changed) {
                    this.listeners = updated;
                }
            }
        }
    }

    public boolean post(Event event) {
        ListenerEntry[] entries = (ListenerEntry[])this.listeners.get(event.getClass());
        if (entries == null) {
            return event.isCancelled();
        } else {
            for (ListenerEntry entry : entries) {
                try {
                    entry.getHandle().invokeExact(event);
                } catch (Throwable error) {
                    logListenerFailure(entry, error);
                }
            }

            return event.isCancelled();
        }
    }

    public boolean hasListeners(Class eventType) {
        ListenerEntry[] entries = (ListenerEntry[])this.listeners.get(eventType);
        return entries != null && entries.length > 0;
    }

    public boolean hasListenerOfType(Class eventType, Class ownerType) {
        ListenerEntry[] entries = (ListenerEntry[])this.listeners.get(eventType);
        if (entries == null) {
            return false;
        } else {
            for (ListenerEntry entry : entries) {
                if (ownerType.isInstance(entry.getTarget())) {
                    return true;
                }
            }

            return false;
        }
    }

    private static boolean isRegistered(Map listenerMap, Object subscriber) {
        for (ListenerEntry[] entries : (Iterable<ListenerEntry[]>)(listenerMap.values())) {
            for (ListenerEntry entry : entries) {
                if (entry.getTarget() == subscriber) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void logListenerFailure(ListenerEntry entry, Throwable error) {
        String listenerName = entry.getTarget().getClass().getName() + "#" + entry.getMethod().getName();
        if (LOGGED_FAILURES.add(listenerName + ":" + error.getClass().getName())) {
            System.err.println(LISTENER_ERROR_PREFIX + listenerName);
            error.printStackTrace();
        }
    }

    static {
        LISTENER_TYPE = MethodType.methodType(void.class, Event.class);
        LOGGED_FAILURES = ConcurrentHashMap.newKeySet();
    }
}
