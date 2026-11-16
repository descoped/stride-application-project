module stride.application.core {

    requires stride.application.config;
    requires stride.application.api;


    requires org.slf4j;
    requires jul.to.slf4j;

    requires com.fasterxml.jackson.databind;

    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.ws.rs;
    requires jakarta.validation;

    requires jakarta.servlet;

    requires org.eclipse.jetty.ee11.servlet;
    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.http2.server;

    requires org.glassfish.jersey.core.common;
    requires org.glassfish.jersey.container.servlet;
    requires org.glassfish.jersey.core.server;
    requires org.glassfish.jersey.inject.hk2;

    requires org.glassfish.hk2.api;
    requires org.glassfish.hk2.locator;
    requires org.glassfish.hk2.runlevel;
    requires org.glassfish.hk2.extras;
    requires org.glassfish.hk2.utilities;

    provides io.descoped.stride.application.spi.ApplicationInitializer with io.descoped.stride.application.core.StrideApplicationInitializer;

    exports io.descoped.stride.application.core;
    exports io.descoped.stride.application.server;
    exports io.descoped.stride.application.cors;
}
