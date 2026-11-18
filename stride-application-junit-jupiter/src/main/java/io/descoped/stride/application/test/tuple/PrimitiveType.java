package io.descoped.stride.application.test.tuple;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public enum PrimitiveType {

    BOOLEAN(Byte.class, Byte.SIZE, false),
    BYTE(Byte.class, Byte.SIZE, false),
    BYTE_ARRAY(Byte[].class, -1, true),
    STRING(String.class, -1, true),
    SHORT(Short.class, Short.SIZE, false),
    INTEGER(Integer.class, Integer.SIZE, false),
    LONG(Long.class, Long.SIZE, false),
    FLOAT(Float.class, Float.SIZE, false),
    DOUBLE(Double.class, Double.SIZE, false);

    private final Class<?> clazz;
    private final int typeSize;
    private final boolean fixed;

    PrimitiveType(Class<?> clazz, int typeSize, boolean fixed) {
        this.clazz = clazz;
        this.typeSize = typeSize;
        this.fixed = fixed;
    }

    public Class<?> clazz() {
        return clazz;
    }

    public int typeSize() {
        return typeSize;
    }

    public boolean isFixed() {
        return fixed;
    }

    public static PrimitiveType typeOf(Object obj) {
        Objects.requireNonNull(obj);
        for (PrimitiveType type : values()) {
            if (obj.getClass().isAssignableFrom(type.clazz)) {
                return type;
            }
        }
        throw new IllegalArgumentException("PrimitiveType for class not supported: " + obj.getClass());
    }

    @Deprecated
    public int sizeOf(Object value) {
        return switch (this) {
            case BYTE_ARRAY -> ((byte[]) value).length;
            case STRING -> ((String) value).getBytes(StandardCharsets.UTF_8).length;
            default -> {
                if (this.isFixed()) {
                    yield this.typeSize;
                } else {
                    throw new IllegalArgumentException("Error determining byte size allocation for type: " + this);
                }
            }
        };
    }

}
