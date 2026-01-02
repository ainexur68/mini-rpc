package top.ainexur.minirpc.serialization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializerRegistryTest {
    @Test
    void serviceLoaderFindsJsonSerializer() {
        SerializerRegistry registry = new SerializerRegistry();
        Serializer serializer = registry.required((byte) 0);
        assertNotNull(serializer);
        assertTrue(serializer instanceof JsonSerializer);
        assertEquals(0, serializer.serializeType());
    }
}
