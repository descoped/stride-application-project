package io.descoped.stride.application.test.tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

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

    public static String printable(Tuple tuple) {
        StringBuilder builder = new StringBuilder();
        builder.append("Format: [type:byte][elementSize:int][elementPayload:byte[]]").append("\n");
        ByteBuffer buffer = ByteBuffer.wrap(tuple.getBytes());
        int elementPosition = 0;
        while (buffer.position() < buffer.limit()) {
            byte typeByte = buffer.get();
            PrimitiveType type = PrimitiveType.values()[typeByte]; // type

            int offset = buffer.getInt(); // offset

            byte[] payload = new byte[offset]; // element
            for (int n = 0; n < offset; n++) {
                payload[n] = buffer.get();
            }
            Tuple.Element<?> element = switch (type) {
                case BOOLEAN -> new Tuple.BooleanElement(payload);
                case BYTE -> new Tuple.ByteElement(payload);
                case BYTE_ARRAY -> new Tuple.ByteArrayElement(payload);
                case STRING -> new Tuple.StringElement(payload);
                case SHORT -> new Tuple.ShortElement(payload);
                case INTEGER -> new Tuple.IntegerElement(payload);
                case LONG -> new Tuple.LongElement(payload);
                case FLOAT -> new Tuple.FloatElement(payload);
                case DOUBLE -> new Tuple.DoubleElement(payload);
            };

            ByteBuffer intBytesBuffer = ByteBuffer.allocate(Integer.SIZE / 8);
            intBytesBuffer.putInt(offset);
            intBytesBuffer.flip();
            List<Integer> intOffsetList = Stream.generate(intBytesBuffer::get).limit(intBytesBuffer.limit()).map(Byte::intValue).toList();

            ByteBuffer payloadBytesBuffer = ByteBuffer.wrap(payload);
            List<Integer> intPayloadList = Stream.generate(payloadBytesBuffer::get).limit(payloadBytesBuffer.limit()).map(Byte::intValue).toList();

            builder.append(elementPosition).append(": ")
                    .append("[").append(type).append("][")
                    .append(offset).append("][")
                    .append(element.value())
                    .append("]")
                    .append(" -> {[")
                    .append(typeByte)
                    .append("], ")
                    .append(intOffsetList)
                    .append(", ")
                    .append(intPayloadList)
                    .append("}")
                    .append("\n");
            elementPosition++;
        }

        return builder.toString();
    }

}
