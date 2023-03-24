package io.descoped.stride.application.core;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InstanceFactory {

    private final Map<String, Object> singletonByType = new ConcurrentHashMap<>();

    public void put(String name, Object instance) {
        singletonByType.put(name, instance);
    }

    public <R> void put(Class<R> clazz, Object instance) {
        singletonByType.put(clazz.getName(), instance);
    }

    @SuppressWarnings("unchecked")
    public <R> R getOrNull(String name) {
        return (R) singletonByType.get(name);
    }

    @SuppressWarnings("unchecked")
    public <R> R getOrNull(Class<R> clazz) {
        return (R) singletonByType.get(clazz.getName());
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(singletonByType.keySet());
    }

    public Collection<Object> instances() {
        return Collections.unmodifiableCollection(singletonByType.values());
    }
}
