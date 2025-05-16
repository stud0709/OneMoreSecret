package com.onemoresecret.bt.layout;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class StrokeSerializer extends JsonSerializer<Stroke> {
    @Override
    public void serialize(Stroke stroke, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        stroke.get().forEach(kr -> {
            try {
                gen.writeObject(kr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        gen.writeEndArray();
    }
}
