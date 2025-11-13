package com.onemoresecret.bt.layout

import android.util.Log
import com.fasterxml.jackson.core.JsonProcessingException
import com.onemoresecret.Util
import java.util.TreeMap
import java.util.function.Consumer
import java.util.stream.Collectors

abstract class KeyboardLayout {
    @JvmField
    protected val layout: MutableMap<Char?, Stroke?> = TreeMap<Char?, Stroke?>()
    abstract fun forKey(c: Char): Stroke?

    /**
     * Convert a [String] to key strokes
     *
     * @param s text to be converted into key strokes
     * @return key strokes
     */
    fun forString(s: String): MutableList<Stroke?> {
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
        val cList: MutableList<Char?> = ArrayList()
        for (c in cArr) {
            cList.add(c)
        }
        val toBeRemoved =
            cList.stream().filter { o -> layout.containsKey(o) }.collect(
                Collectors.toSet()
            )
        toBeRemoved.forEach(Consumer { o: Char? -> layout.remove(o) })
    }

    fun logLayout() {
        layout.entries.forEach(Consumer { entry: MutableMap.MutableEntry<Char?, Stroke?>? ->
            try {
                Log.d(this::class.qualifiedName, Util.JACKSON_MAPPER.writeValueAsString(entry))
            } catch (e: JsonProcessingException) {
                e.printStackTrace()
            }
        })
    }

    companion object {
        @JvmField
        val knownSubclasses: Array<Class<*>> = arrayOf<Class<*>>(
            USLayout::class.java,
            GermanLayout::class.java,
            SwissLayout::class.java
        )
    }
}
