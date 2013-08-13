package com.yrek.jackson.dataformat.protobuf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
        DOUBLE,
        FLOAT,
        INT32,
        INT64,
        UINT32,
        UINT64,
        SINT32,
        SINT64,
        FIXED32,
        FIXED64,
        SFIXED32,
        SFIXED64,
        BOOL,
        STRING,
        BYTES,
            ;

        public final String protobufName = name().toLowerCase();
    }
}
