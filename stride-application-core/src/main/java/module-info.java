module stride.application.core {

    requires stride.application.config;
    requires stride.application.api;

    requires java.net.http;
    requires com.nimbusds.jose.jwt;

    requires org.slf4j;
    requires jul.to.slf4j;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires com.fasterxml.jackson.module.jakarta.xmlbind;

    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.ws.rs;
    requires jakarta.validation;
    requires jakarta.xml.bind;
    requires jakarta.activation;

    requires jakarta.servlet;
    requires org.eclipse.jetty.xml;

    requires io.swagger.v3.core;
    requires io.swagger.v3.jaxrs2;
    requires io.swagger.v3.oas.annotations;
    requires io.swagger.v3.oas.integration;
    requires io.swagger.v3.oas.models;

    requires org.eclipse.jetty.ee11.servlet;
    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.http2.server;
    requires org.eclipse.jetty.ee11.websocket.jetty.server;

    requires org.glassfish.jersey.core.common;
    requires org.glassfish.jersey.core.client;
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
    exports io.descoped.stride.application.openapi;
    exports io.descoped.stride.application.security;
}
