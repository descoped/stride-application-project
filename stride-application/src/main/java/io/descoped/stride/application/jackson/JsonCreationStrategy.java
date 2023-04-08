package io.descoped.stride.application.jackson;

/**
 * Creational behaviour during node navigation.
 */
public enum JsonCreationStrategy {
    /**
     * Default behaviour
     */
    STRICT,

    /**
     * Dynamically create and add new ObjectNode and ArrayNode
     */
    CREATE_NODE_IF_NOT_EXIST,

    /**
     * Create new ObjectNode or ArrayNode for with and at during navigation
     */
    CREATE_EPHEMERAL_NODE_IF_NOT_EXIST
}
