package com.onemoresecret.bt.layout

import android.util.Log
import com.fasterxml.jackson.core.JsonProcessingException
import com.onemoresecret.Util
import java.util.TreeMap
import java.util.function.Consumer
import java.util.stream.Collectors

abstract class KeyboardLayout {
    protected val layout: MutableMap<Char, Stroke> = TreeMap()
    abstract fun forKey(c: Char): Stroke?

    /**
     * Convert a [String] to key strokes
     *
     * @param s text to be converted into key strokes
     * @return key strokes
     */
    fun forString(s: String): List<Stroke?> {
        val cArr = s.toCharArray()
        val list: MutableList<Stroke?> = ArrayList()

        for (c in cArr) {
            list.add(forKey(c))
        }

        return list
    }

    /**
     * Remove [Stroke]s associated with the parent layout, but not present in the current one.
     * @param cArr characters to remove
     */
    protected fun remove(vararg cArr: Char) {
        val cList: MutableList<Char> = ArrayList()
        for (c in cArr) {
            cList.add(c)
        }
        val toBeRemoved = cList.stream().filter { key: Char -> layout.containsKey(key) }.collect(
            Collectors.toSet()
        )
        toBeRemoved.forEach(Consumer { key: Char -> layout.remove(key) })
    }

    fun logLayout() {
        layout.entries.forEach(Consumer<Map.Entry<Char, Stroke>> { entry: Map.Entry<Char, Stroke>? ->
            try {
                Log.d(javaClass.name, Util.JACKSON_MAPPER.writeValueAsString(entry))
            } catch (e: JsonProcessingException) {
                e.printStackTrace()
            }
        })
    }

    companion object {
        val knownSubclasses: Array<Class<*>> = arrayOf(
            USLayout::class.java,
            GermanLayout::class.java,
            SwissLayout::class.java
        )
    }
}
