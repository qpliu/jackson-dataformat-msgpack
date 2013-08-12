package com.yrek.jackson.dataformat.msgpack;

import java.util.HashMap;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.type.TypeBindings;

class IntrospectionResults {
    private DeserializationConfig _deserializationConfig;
    private HashMap<JavaType,HashMap<Integer,String>> _names;
    private HashMap<JavaType,HashMap<String,JavaType>> _types;

    private SerializationConfig _serializationConfig;
    private HashMap<JavaType,HashMap<String,Integer>> _keys;

    IntrospectionResults(DeserializationConfig deserializationConfig) {
        _deserializationConfig = deserializationConfig;
        _names = new HashMap<JavaType,HashMap<Integer,String>>();
        _types = new HashMap<JavaType,HashMap<String,JavaType>>();
    }

    IntrospectionResults(SerializationConfig serializationConfig) {
        _serializationConfig = serializationConfig;
        _keys = new HashMap<JavaType,HashMap<String,Integer>>();
    }

    private void introspectForDeserialization(JavaType javaType) {
        HashMap<Integer,String> names = new HashMap<Integer,String>();
        _names.put(javaType, names);
        HashMap<String,JavaType> types = new HashMap<String,JavaType>();
        _types.put(javaType, types);
        BeanDescription beanDescription = _deserializationConfig.introspect(javaType);
        for (BeanPropertyDefinition bpd : beanDescription.findProperties())
            if (bpd.couldDeserialize()) {
                types.put(bpd.getName(), bpd.getMutator().getType(new TypeBindings(_deserializationConfig.getTypeFactory(), javaType)));
                if (bpd.getMutator().hasAnnotation(MessagePackMapKey.class)) {
                    names.put(bpd.getMutator().getAnnotation(MessagePackMapKey.class).value(), bpd.getName());
                }
            }
    }

    public String getName(JavaType javaType, int key) {
        HashMap<Integer,String> names = _names.get(javaType);
        if (names == null) {
            introspectForDeserialization(javaType);
            names = _names.get(javaType);
        }
        return names.get(key);
    }

    public JavaType getType(JavaType javaType, String name) {
        HashMap<String,JavaType> types = _types.get(javaType);
        if (types == null) {
            introspectForDeserialization(javaType);
            types = _types.get(javaType);
        }
        return types.get(name);
    }

    private void introspectForSerialization(JavaType javaType) {
        HashMap<String,Integer> keys = new HashMap<String,Integer>();
        _keys.put(javaType, keys);
        BeanDescription beanDescription = _serializationConfig.introspect(javaType);
        for (BeanPropertyDefinition bpd : beanDescription.findProperties())
            if (bpd.couldSerialize() && bpd.getAccessor().hasAnnotation(MessagePackMapKey.class))
                keys.put(bpd.getName(), bpd.getMutator().getAnnotation(MessagePackMapKey.class).value());
    }

    public Integer getKey(JavaType javaType, String name) {
        HashMap<String,Integer> keys = _keys.get(javaType);
        if (keys == null) {
            introspectForSerialization(javaType);
            keys = _keys.get(javaType);
        }
        return keys.get(name);
    }
}
