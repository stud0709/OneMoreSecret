package com.onemoresecret.bt;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class KeyboardReportSerializer extends JsonSerializer<KeyboardReport> {
    @Override
    public void serialize(KeyboardReport keyboardReport, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("usage", keyboardReport.getUsage().toString());
        gen.writeArrayFieldStart("modifiers");
        keyboardReport.getModifiers().stream().map(Enum::toString).forEach(s -> {
            try {
                gen.writeString(s);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
