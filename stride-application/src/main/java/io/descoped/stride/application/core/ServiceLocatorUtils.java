package io.descoped.stride.application.core;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;

public class ServiceLocatorUtils {

    public static ServiceLocator instance() {
        return ServiceLocatorFactory.getInstance().create("default");
    }

    public static ServiceLocator instance(String named) {
        return ServiceLocatorFactory.getInstance().create(named);
    }
}
