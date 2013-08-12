package com.yrek.jackson.dataformat.msgpack;

import java.lang.reflect.Field;
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

    private HashMap<JavaType,HashMap<String,Integer>> _enumInt;
    private HashMap<JavaType,HashMap<Integer,String>> _enumString;

    IntrospectionResults(DeserializationConfig deserializationConfig) {
        _deserializationConfig = deserializationConfig;
        _names = new HashMap<JavaType,HashMap<Integer,String>>();
        _types = new HashMap<JavaType,HashMap<String,JavaType>>();
        _enumInt = new HashMap<JavaType,HashMap<String,Integer>>();
        _enumString = new HashMap<JavaType,HashMap<Integer,String>>();
    }

    IntrospectionResults(SerializationConfig serializationConfig) {
        _serializationConfig = serializationConfig;
        _keys = new HashMap<JavaType,HashMap<String,Integer>>();
        _enumInt = new HashMap<JavaType,HashMap<String,Integer>>();
        _enumString = new HashMap<JavaType,HashMap<Integer,String>>();
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

    private void introspectEnums(JavaType javaType) {
        assert javaType.isEnumType();
        HashMap<String,Integer> enumInt = new HashMap<String,Integer>();
        _enumInt.put(javaType, enumInt);
        HashMap<Integer,String> enumString = new HashMap<Integer,String>();
        _enumString.put(javaType, enumString);

        // can't have mixin annotations - are they available for enum values anyhow?
        Class<?> rawClass = javaType.getRawClass();
        for (Object value : rawClass.getEnumConstants()) {
            try {
                String name = ((Enum) value).name();
                MessagePackEnum annotation = rawClass.getField(name).getAnnotation(MessagePackEnum.class);
                if (annotation != null) {
                    enumInt.put(name, annotation.value());
                    enumString.put(annotation.value(), name);
                }
            } catch (Exception e) {
            }
        }
    }

    public Integer getEnum(JavaType javaType, String name) {
        if (!javaType.isEnumType())
            return null;
        HashMap<String,Integer> enumInt = _enumInt.get(javaType);
        if (enumInt == null) {
            introspectEnums(javaType);
            enumInt = _enumInt.get(javaType);
        }
        return enumInt.get(name);
    }

    public String getEnum(JavaType javaType, int value) {
        if (!javaType.isEnumType())
            return null;
        HashMap<Integer,String> enumString = _enumString.get(javaType);
        if (enumString == null) {
            introspectEnums(javaType);
            enumString = _enumString.get(javaType);
        }
        return enumString.get(value);
    }
}
