module stride.application.api {

    requires stride.application.config;

    requires org.slf4j;
    requires jul.to.slf4j;
    requires java.logging;

    requires org.glassfish.hk2.api;

    uses io.descoped.stride.application.spi.ApplicationInitializer;

    exports io.descoped.stride.application.api;
    exports io.descoped.stride.application.spi;

}