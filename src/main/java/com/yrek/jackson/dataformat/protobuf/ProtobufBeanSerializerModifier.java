package com.yrek.jackson.dataformat.protobuf;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

public class ProtobufBeanSerializerModifier extends BeanSerializerModifier {
    private static final Comparator<BeanPropertyWriter> comparator = new Comparator<BeanPropertyWriter>() {
        public int compare(BeanPropertyWriter b1, BeanPropertyWriter b2) {
            Protobuf p1 = b1.getAnnotation(Protobuf.class);
            Protobuf p2 = b2.getAnnotation(Protobuf.class);
            int n1 = 0;
            int n2 = 0;
            if (p1 != null)
                n1 = p1.value();
            if (p2 != null)
                n2 = p2.value();
            return n1 - n2;
        }
    };

    /**
     * Method called by {@link BeanSerializerFactory} with set of properties
     * to serialize, in default ordering (based on defaults as well as 
     * possible type annotations).
     * Implementations can change ordering any way they like.
     *
     * Properties <code>List</code> passed as argument is modifiable, and returned List must
     * likewise be modifiable as it may be passed to multiple registered
     * modifiers.
     */
    public List<BeanPropertyWriter> orderProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
        Collections.sort(beanProperties, comparator);
        return beanProperties;
    }
}
