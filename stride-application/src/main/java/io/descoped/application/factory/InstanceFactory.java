package io.descoped.application.factory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InstanceFactory {

    private final Map<String, Object> singletonByType = new ConcurrentHashMap<>();

    public void put(String name, Object instance) {
        singletonByType.put(name, instance);
    }

    public <R> void put(Class<R> clazz, Object instance) {
        singletonByType.put(clazz.getName(), instance);
    }

    public <R> R getOrNull(String name) {
        return (R) singletonByType.get(name);
    }

    public <R> R getOrNull(Class<R> clazz) {
        return (R) singletonByType.get(clazz.getName());
    }
}
