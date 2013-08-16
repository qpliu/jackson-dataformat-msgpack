package com.yrek.jackson.dataformat.protobuf;

import java.io.IOException;

import com.fasterxml.jackson.databind.JavaType;

class MessageField {
    private final String protobufName;
    private final String jsonName;
    private final JavaType javaType;
    private final Protobuf protobuf;

    MessageField(String jsonName, JavaType javaType, Protobuf protobuf) {
        this.protobufName = protobuf.name().length() > 0 ? protobuf.name() : jsonName;
        this.jsonName = jsonName;
        this.javaType = javaType;
        this.protobuf = protobuf;
    }

    public String getName() {
        return jsonName;
    }

    public int getTag() {
        return protobuf.value();
    }

    public boolean isRepeated() {
        if (!javaType.isContainerType() || javaType.isMapLikeType())
            return false;
        if (javaType.getRawClass() == byte[].class)
            return false;
        return true;
    }

    public boolean isPacked() {
        if (!protobuf.packed())
            return false;
        if (!isRepeated())
            return false;
        switch (protobuf.type()) {
        case DEFAULT:
            break;
        case STRING:
        case BYTES:
            return false;
        default:
            return true;
        }
        JavaType elementType = javaType.containedType(0);
        if (elementType.isEnumType())
            return true;
        Class<?> c = elementType.getRawClass();
        return c == boolean.class || c == Boolean.class || c == byte.class || c == Byte.class || c == short.class || c == Short.class || c == int.class || c == Integer.class || c == long.class || c == Long.class || c == float.class || c == Float.class || c == double.class || c == Double.class;
    }

    public WireType packedWireType() {
        if (!isPacked())
            throw new IllegalStateException();
        switch (protobuf.type()) {
        case DOUBLE: case FIXED64: case SFIXED64:
            return WireType.Fixed64;
        case FLOAT: case FIXED32: case SFIXED32:
            return WireType.Fixed32;
        case DEFAULT:
            break;
        default:
            return WireType.Varint;
        }
        JavaType elementType = javaType.containedType(0);
        if (elementType.isEnumType())
            return WireType.Varint;
        Class<?> c = elementType.getRawClass();
        if (c == float.class || c == Float.class)
            return WireType.Fixed32;
        if (c == double.class || c == Double.class)
            return WireType.Fixed64;
        return WireType.Varint;
    }

    public boolean isEnumType() {
        return getElementJavaType().isEnumType();
    }

    public boolean isBoolean() {
        Class<?> c = getElementJavaType().getRawClass();
        return c == boolean.class || c == Boolean.class;
    }

    public boolean isMessageType() {
        if (protobuf.type() != Protobuf.Type.DEFAULT)
            return false;
        return Protobuf.Type.getDefault(getElementJavaType().getRawClass()) == null;
    }

    public Protobuf.Type getProtobufType() {
        return protobuf.type();
    }

    public String getProtobufDefinition() {
        try {
            return getProtobufDefinition(new StringBuilder()).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Appendable getProtobufDefinition(Appendable appendable) throws IOException {
        if (isRepeated())
            appendable.append("repeated ");
        else if (protobuf.required())
            appendable.append("required ");
        else
            appendable.append("optional ");
        if (protobuf.type() != Protobuf.Type.DEFAULT) {
            appendable.append(protobuf.type().protobufName);
        } else {
            JavaType elementJavaType = getElementJavaType();
            Protobuf.Type type = Protobuf.Type.getDefault(elementJavaType.getRawClass());
            if (type != null)
                appendable.append(type.protobufName);
            else
                appendable.append(MessageDescription.getProtobufName(elementJavaType));
        }
        appendable.append(" ").append(protobufName).append(" = ").append(String.valueOf(protobuf.value()));
        if (isPacked())
            appendable.append(" [packed=true]");
        return appendable;
    }

    JavaType getElementJavaType() {
        if (!javaType.isContainerType())
            return javaType;
        if (javaType.getRawClass() == byte[].class)
            return javaType;
        return javaType.containedType(0);
    }
}
