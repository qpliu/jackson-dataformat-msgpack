package com.yrek.jackson.dataformat.protobuf;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.type.TypeBindings;

class MessageDescription {
    private final String messageName;
    private final HashMap<String,MessageField> byName;
    private final TreeMap<Integer,MessageField> byTag;

    MessageDescription(BeanDescription beanDescription, TypeBindings typeBindings) {
        this(getProtobufName(beanDescription.getType()), beanDescription, typeBindings);
    }

    MessageDescription(String messageName, BeanDescription beanDescription, TypeBindings typeBindings) {
        this.messageName = messageName;
        this.byName = new HashMap<String,MessageField>();
        this.byTag = new TreeMap<Integer,MessageField>();

        for (BeanPropertyDefinition bpd : beanDescription.findProperties()) {
            AnnotatedMember annotated = bpd.getAccessor();
            Protobuf protobuf = null;
            if (annotated != null)
                protobuf = annotated.getAnnotation(Protobuf.class);
            if (protobuf == null)
                annotated = bpd.getMutator();
            if (annotated != null)
                protobuf = annotated.getAnnotation(Protobuf.class);
            if (protobuf == null)
                continue;
            MessageField messageField = new MessageField(bpd.getName(), annotated.getType(typeBindings), protobuf);
            byName.put(messageField.getName(), messageField);
            byTag.put(messageField.getTag(), messageField);
        }
    }

    public MessageField getMessageField(String name) {
        return byName.get(name);
    }

    public MessageField getMessageField(int tag) {
        return byTag.get(tag);
    }

    public Iterable<MessageField> getMessageFields() {
        return byTag.values();
    }

    public String getProtobufDefinition() {
        return getProtobufDefinition(new StringBuilder()).toString();
    }

    public StringBuilder getProtobufDefinition(StringBuilder stringBuilder) {
        stringBuilder.append("message ").append(messageName).append(" {\n");
        for (MessageField messageField : getMessageFields())
            messageField.getProtobufDefinition(stringBuilder.append("  ")).append(";\n");
        return stringBuilder.append("}\n");
    }

    static String getProtobufName(Class<?> cl) {
        if (cl == double.class || cl == Double.class)
            return "double";
        if (cl == float.class || cl == Float.class)
            return "float";
        if (cl == byte.class || cl == Byte.class || cl == short.class || cl == Short.class || cl == int.class || cl == Integer.class)
            return "int32";
        if (cl == long.class || cl == Long.class)
            return "int64";
        if (cl == boolean.class || cl == Boolean.class)
            return "bool";
        if (cl == String.class)
            return "string";
        if (cl == byte[].class)
            return "bytes";
        
        Protobuf protobuf = cl.getAnnotation(Protobuf.class);
        if (protobuf != null && protobuf.name().length() > 0)
            return protobuf.name();
        String name = cl.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    static String getProtobufName(JavaType javaType) {
        String name = getProtobufName(javaType.getRawClass());
        for (int i = 0; i < javaType.containedTypeCount(); i++)
            name += getProtobufName(javaType.containedType(i));
        return name;
    }
}
