package com.yrek.jackson.dataformat.protobuf;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;

public class ProtobufSchema {
    private SerializationConfig serializationConfig;
    private HashMap<JavaType,MessageDescription> messages;
    private HashMap<JavaType,EnumDescription> enums;

    ProtobufSchema(SerializationConfig serializationConfig) {
        this.serializationConfig = serializationConfig;
        this.messages = new HashMap<JavaType,MessageDescription>();
        this.enums = new HashMap<JavaType,EnumDescription>();
    }

    private BeanDescription introspect(JavaType javaType) {
        return serializationConfig.introspect(javaType);
    }

    void collectType(JavaType javaType) throws NoSuchFieldException {
        if (javaType.isEnumType()) {
            if (enums.containsKey(javaType))
                return;
            enums.put(javaType, new EnumDescription(javaType.getRawClass()));
            return;
        }
        if (messages.containsKey(javaType))
            return;
        MessageDescription messageDescription = new MessageDescription(introspect(javaType));
        messages.put(javaType, messageDescription);
        for (MessageField messageField : messageDescription.getMessageFields())
            if (messageField.isEnumType() || messageField.isMessageType())
                collectType(messageField.getElementJavaType());
    }

    MessageDescription getMessageDescription(JavaType javaType) {
        return messages.get(javaType);
    }

    MessageDescription getMessageDescription(MessageField messageField) {
        return messages.get(messageField.getElementJavaType());
    }

    EnumDescription getEnumDescription(JavaType javaType) {
        return enums.get(javaType);
    }

    EnumDescription getEnumDescription(MessageField messageField) {
        return enums.get(messageField.getElementJavaType());
    }

    public String getProtobufDefinition() {
        try {
            return getProtobufDefinition(new StringBuilder()).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Appendable getProtobufDefinition(Appendable appendable) throws IOException {
        HashSet<JavaType> empty = new HashSet<JavaType>();
        for (;;) {
            int size = empty.size();
            for (Map.Entry<JavaType,MessageDescription> e : messages.entrySet()) {
                if (empty.contains(e.getKey()))
                    continue;
                int count = 0;
                for (MessageField messageField : e.getValue().getMessageFields())
                    if (!empty.contains(messageField.getElementJavaType()))
                        count++;
                if (count == 0)
                    empty.add(e.getKey());
            }
            if (empty.size() == size)
                break;
        }
        for (EnumDescription enumDescription : enums.values())
            enumDescription.getProtobufDefinition(appendable);
        for (Map.Entry<JavaType,MessageDescription> e : messages.entrySet())
            if (!empty.contains(e.getKey()))
                e.getValue().getProtobufDefinition(appendable, empty);
        return appendable;
    }
}
