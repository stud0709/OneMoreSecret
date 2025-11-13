package com.onemoresecret.bt

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.io.IOException

class KeyboardReportSerializer : JsonSerializer<KeyboardReport>() {
    @Throws(IOException::class)
    override fun serialize(
        keyboardReport: KeyboardReport,
        gen: JsonGenerator,
        serializers: SerializerProvider?
    ) {
        gen.writeStartObject()
        gen.writeStringField("usage", keyboardReport.usage.toString())
        gen.writeArrayFieldStart("modifiers")
        keyboardReport.modifiers.stream()
            .map<String?> { obj -> obj.toString() }
            .forEach { s: String ->
                try {
                    gen.writeString(s)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        gen.writeEndArray()
        gen.writeEndObject()
    }
}
