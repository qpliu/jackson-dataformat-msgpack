package com.yrek.jackson.dataformat.msgpack;

import java.util.EnumSet;

class MessagePackFeature {
    public interface Feature {
        public boolean enabledByDefault();
    }

    public static <F extends Enum<F> & Feature> EnumSet<F> defaults(Class<F> c) {
        EnumSet<F> defaults = EnumSet.noneOf(c);
        for (F f : c.getEnumConstants())
            if (f.enabledByDefault())
                defaults.add(f);
        return defaults;
    }
}
