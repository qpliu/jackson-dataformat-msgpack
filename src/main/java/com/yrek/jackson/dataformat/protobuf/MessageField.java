package com.yrek.jackson.dataformat.protobuf;

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

    public boolean isPacked() {
        if (!protobuf.packed())
            return false;
        if ((javaType.isContainerType() || javaType.isCollectionLikeType()) && !javaType.isMapLikeType())
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

    public String getProtobufDefinition() {
        return getProtobufDefinition(new StringBuilder()).toString();
    }

    public StringBuilder getProtobufDefinition(StringBuilder stringBuilder) {
        if ((javaType.isContainerType() || javaType.isCollectionLikeType()) && !javaType.isMapLikeType())
            stringBuilder.append("repeated ");
        else if (protobuf.required())
            stringBuilder.append("required ");
        else
            stringBuilder.append("optional ");
        if (protobuf.type() != Protobuf.Type.DEFAULT)
            stringBuilder.append(protobuf.type().protobufName);
        else
            stringBuilder.append(MessageDescription.getProtobufName(javaType));
        stringBuilder.append(" ").append(protobufName).append(" = ").append(protobuf.value());
        if (isPacked())
            stringBuilder.append(" [packed=true]");
        return stringBuilder;
    }
}
