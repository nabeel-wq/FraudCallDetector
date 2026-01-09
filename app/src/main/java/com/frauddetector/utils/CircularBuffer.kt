package com.frauddetector.utils

/**
 * Circular buffer for maintaining a sliding window of items.
 * Automatically overwrites oldest items when capacity is reached.
 */
class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = ArrayList<T>(capacity)
    private var writeIndex = 0
    
    /**
     * Add an item to the buffer.
     * If buffer is full, overwrites the oldest item.
     */
    fun add(item: T) {
        if (buffer.size < capacity) {
            buffer.add(item)
        } else {
            buffer[writeIndex] = item
            writeIndex = (writeIndex + 1) % capacity
        }
    }
    
    /**
     * Get all items in the buffer (in chronological order).
     */
    fun getAll(): List<T> {
        return if (buffer.size < capacity) {
            buffer.toList()
        } else {
            // Reorder to chronological order
            val result = ArrayList<T>(capacity)
            for (i in 0 until capacity) {
                val index = (writeIndex + i) % capacity
                result.add(buffer[index])
            }
            result
        }
    }
    
    /**
     * Get the most recent item.
     */
    fun getLast(): T? {
        return if (buffer.isEmpty()) null
        else if (buffer.size < capacity) buffer.last()
        else buffer[(writeIndex - 1 + capacity) % capacity]
    }
    
    /**
     * Get the number of items currently in the buffer.
     */
    val size: Int
        get() = buffer.size
    
    /**
     * Check if buffer is empty.
     */
    fun isEmpty(): Boolean = buffer.isEmpty()
    
    /**
     * Check if buffer is full.
     */
    fun isFull(): Boolean = buffer.size >= capacity
    
    /**
     * Clear all items from the buffer.
     */
    fun clear() {
        buffer.clear()
        writeIndex = 0
    }
    
    /**
     * Join all items to a string with separator.
     */
    fun joinToString(separator: String = " "): String {
        return getAll().joinToString(separator)
    }
}
