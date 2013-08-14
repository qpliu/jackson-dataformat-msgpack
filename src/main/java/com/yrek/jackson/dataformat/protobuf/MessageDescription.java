package com.yrek.jackson.dataformat.protobuf;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

class MessageDescription {
    private final String messageName;
    private final HashMap<String,MessageField> byName;
    private final TreeMap<Integer,MessageField> byTag;

    MessageDescription(BeanDescription beanDescription) {
        this(getProtobufName(beanDescription.getType()), beanDescription);
    }

    MessageDescription(String messageName, BeanDescription beanDescription) {
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
            MessageField messageField = new MessageField(bpd.getName(), annotated.getType(beanDescription.bindingsForBeanType()), protobuf);
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
        try {
            return getProtobufDefinition(new StringBuilder()).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Appendable getProtobufDefinition(Appendable appendable) throws IOException {
        appendable.append("message ").append(messageName).append(" {\n");
        for (MessageField messageField : getMessageFields())
            messageField.getProtobufDefinition(appendable.append("  ")).append(";\n");
        return appendable.append("}\n");
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
        return name.substring(Math.max(name.lastIndexOf('.'),name.lastIndexOf('$')) + 1);
    }

    static String getProtobufName(JavaType javaType) {
        String name = getProtobufName(javaType.getRawClass());
        for (int i = 0; i < javaType.containedTypeCount(); i++)
            name += getProtobufName(javaType.containedType(i));
        return name;
    }
}
