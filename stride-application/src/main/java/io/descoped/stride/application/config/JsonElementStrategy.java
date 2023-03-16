package io.descoped.stride.application.config;

// TODO Refactor with/at in JsonElement to its own type.

/**
 * Creational behaviour during node navigation.
 */
public enum JsonElementStrategy {
    /**
     * Default behaviour
     */
    FAIL_FAST,

    /**
     * Create new ObjectNode or ArrayNode for with and at during navigation
     */
    CREATE_EPHEMERAL_NODE_IF_NOT_EXIST
}
