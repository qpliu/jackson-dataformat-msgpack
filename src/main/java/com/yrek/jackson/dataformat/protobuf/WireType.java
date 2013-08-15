package com.yrek.jackson.dataformat.protobuf;

public enum WireType {
    Varint,
    Fixed64,
    LengthDelimited,
    StartGroup,
    EndGroup,
    Fixed32,
        ;

    public int getKey(int tag) {
        return (tag << 3) | ordinal();
    }

    public static WireType getWireType(int key) {
        if ((key&7) >= values().length)
            return null;
        return values()[key&7];
    }
}
