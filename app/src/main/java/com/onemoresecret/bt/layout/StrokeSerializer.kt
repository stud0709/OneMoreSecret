package com.onemoresecret.bt.layout

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.onemoresecret.bt.KeyboardReport
import java.io.IOException
import java.util.function.Consumer

class StrokeSerializer : JsonSerializer<Stroke?>() {
    @Throws(IOException::class)
    override fun serialize(stroke: Stroke?, gen: JsonGenerator, serializers: SerializerProvider?) {
        gen.writeStartArray()
        stroke?.get()?.forEach(Consumer { kr: KeyboardReport? ->
            try {
                gen.writeObject(kr)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        })
        gen.writeEndArray()
    }
}
