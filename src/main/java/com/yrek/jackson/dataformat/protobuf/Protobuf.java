package com.yrek.jackson.dataformat.protobuf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;

/**
 * Mark an enum value to serialize and deserialize as an integer.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Protobuf {
    int value();
    String name() default "";
    boolean packed() default true;
    boolean required() default false;
    Type type() default Type.DEFAULT;

    public enum Type {
        DEFAULT,
        DOUBLE(double.class, Double.class),
        FLOAT(float.class, Float.class),
        INT32(byte.class, Byte.class, short.class, Short.class, int.class, Integer.class),
        INT64(long.class, Long.class),
        UINT32,
        UINT64,
        SINT32,
        SINT64,
        FIXED32,
        FIXED64,
        SFIXED32,
        SFIXED64,
        BOOL(boolean.class, Boolean.class),
        STRING(String.class),
        BYTES(byte[].class),
            ;

        private static HashMap<Class<?>,Type> defaults;
        private Class<?>[] cls;

        private Type() {}

        private Type(Class<?>... cls) {
            this.cls = cls;
        }

        public static Type getDefault(Class<?> cl) {
            if (defaults == null) {
                HashMap<Class<?>,Type> map = new HashMap<Class<?>,Type>();
                for (Type t : values())
                    if (t.cls != null)
                        for (Class<?> c : t.cls)
                            map.put(c, t);
                defaults = map;
            }
            return defaults.get(cl);
        }

        public final String protobufName = name().toLowerCase();
    }
}
