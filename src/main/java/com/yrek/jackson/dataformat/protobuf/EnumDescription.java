package com.yrek.jackson.dataformat.protobuf;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

class EnumDescription {
    private final String enumName;
    private final HashMap<String,Integer> values;
    private final TreeMap<Integer,String> names;
    private final TreeMap<Integer,String> protobufNames;

    EnumDescription(Class<?> cl) throws NoSuchFieldException {
        assert(cl.isEnum());
        this.enumName = MessageDescription.getProtobufName(cl);
        this.values = new HashMap<String,Integer>();
        this.names = new TreeMap<Integer,String>();
        this.protobufNames = new TreeMap<Integer,String>();

        for (Object object : cl.getEnumConstants()) {
            String fieldName = ((Enum) object).name();
            String protobufName = fieldName;
            Protobuf protobuf = cl.getField(fieldName).getAnnotation(Protobuf.class);
            if (protobuf == null)
                continue;
            if (protobuf.name().length() > 0)
                protobufName = protobuf.name();
            values.put(fieldName, protobuf.value());
            names.put(protobuf.value(), fieldName);
            protobufNames.put(protobuf.value(), protobufName);
        }
    }

    public String getName() {
        return enumName;
    }

    public String getName(int value) {
        return names.get(value);
    }

    public Integer getValue(String name) {
        return values.get(name);
    }

    public Iterable<String> getNames() {
        return names.values();
    }

    public String getProtobufDefinition() {
        return getProtobufDefinition(new StringBuilder()).toString();
    }

    public StringBuilder getProtobufDefinition(StringBuilder stringBuilder) {
        stringBuilder.append("enum ").append(getName()).append(" {\n");
        for (Map.Entry<Integer,String> e : protobufNames.entrySet())
            stringBuilder.append("  ").append(e.getValue()).append(" = ").append(e.getKey()).append(";\n");
        return stringBuilder.append("}\n");
    }
}
