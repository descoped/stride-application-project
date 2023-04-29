package io.descoped.stride.application.test.tuple;

import java.nio.charset.StandardCharsets;

public class TupleHelper {

    public static Tuple from(String representation, String delimiter, PrimitiveType... typeMapping) {
        String[] elements = representation.replaceFirst("^" + delimiter, "").split(delimiter);

        TupleBuilder tupleBuilder = new TupleBuilder();

        for (int i = 0; i < elements.length; i++) {
            String element = elements[i];
            PrimitiveType type = typeMapping.length == 0 ? null : typeMapping[i];

            if (type == null) {
                tupleBuilder.add(element);
            } else {
                switch (type) {
                    case BOOLEAN -> tupleBuilder.add(Boolean.TRUE.equals(Boolean.parseBoolean(element)));
                    case BYTE -> tupleBuilder.add(Byte.parseByte(element));
                    case BYTE_ARRAY -> tupleBuilder.add(element.getBytes(StandardCharsets.UTF_8));
                    case STRING -> tupleBuilder.add(element);
                    case SHORT -> tupleBuilder.add(Short.parseShort(element));
                    case INTEGER -> tupleBuilder.add(Integer.parseInt(element));
                    case LONG -> tupleBuilder.add(Long.parseLong(element));
                    case FLOAT -> tupleBuilder.add(Float.parseFloat(element));
                    case DOUBLE -> tupleBuilder.add(Double.parseDouble(element));
                    default -> throw new IllegalStateException();
                }
            }
        }
        return tupleBuilder.pack();
    }

    public static StringBuilder printGroups(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        // TODO iterate over elements and print each group
        return builder;
    }

}
