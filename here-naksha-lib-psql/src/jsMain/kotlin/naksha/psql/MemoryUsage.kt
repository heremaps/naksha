package naksha.psql

/**
 * The memory usage as defined by [Plv8] as specified in [v8_getheapstatistics](https://nodejs.org/api/v8.html#v8_v8_getheapstatistics).
 * @since 3.0
 */
external interface MemoryUsage {
    /**
     * The value of total_heap_size is the number of bytes V8 has allocated for the heap. This can grow if used_heap needs more memory.
     * @since 3.0
     */
    val total_heap_size: Int

    /**
     * The value of total_heap_size_executable is the portion of the heap that can contain executable code, in bytes. This includes memory used by JIT-compiled code and any memory that must be kept executable.
     * @since 3.0
     */
    val total_heap_size_executable: Int

    /**
     * The value of total_physical_size is the actual physical memory used by the V8 heap, in bytes. This is the amount of memory that is committed (or in use) rather than reserved.
     * @since 3.0
     */
    val total_physical_size: Int

    /**
     * The value of total_available_size is the number of bytes of memory available to the V8 heap. This value represents how much more memory V8 can use before it exceeds the heap limit.
     * @since 3.0
     */
    val total_available_size: Int

    /**
     * The value of used_heap_size is number of bytes currently being used by V8’s JavaScript objects. This is the actual memory in use and does not include memory that has been allocated but not yet used.
     * @since 3.0
     */
    val used_heap_size: Int

    /**
     * The value of heap_size_limit is the maximum size of the V8 heap, in bytes (either the default limit, determined by system resources, or the value passed to the --max_old_space_size option).
     * @since 3.0
     */
    val heap_size_limit: Int

    /**
     * The value of malloced_memory is the number of bytes allocated through malloc by V8.
     * @since 3.0
     */
    val malloced_memory: Int

    /**
     * The value of peak_malloced_memory is the peak number of bytes allocated through malloc by V8 during the lifetime of the process.
     * @since 3.0
     */
    val peak_malloced_memory: Int

    /**
     * A 0/1 boolean, which signifies whether the --zap_code_space option is enabled or not. This makes V8 overwrite heap garbage with a bit pattern. The RSS footprint (resident set size) gets bigger because it continuously touches all heap pages and that makes them less likely to get swapped out by the operating system.
     * @since 3.0
     */
    val does_zap_garbage: Int

    /**
     * The value of native_context is the number of the top-level contexts currently active. Increase of this number over time indicates a memory leak.
     * @since 3.0
     */
    val number_of_native_contexts: Int

    /**
     * The value of detached_context is the number of contexts that were detached and not yet garbage collected. This number being non-zero indicates a potential memory leak.
     * @since 3.0
     */
    val number_of_detached_contexts: Int

    /**
     * The value of total_global_handles_size is the total memory size of V8 global handles.
     * @since 3.0
     */
    val total_global_handles_size: Int

    /**
     * The value of used_global_handles_size is the used memory size of V8 global handles.
     * @since.30
     */
    val used_global_handles_size: Int

    /**
     * The value of external_memory is the memory size of array buffers and external strings.
     * @since 3.0
     */
    val external_memory: Int

    /**
     * The value of total allocated bytes since the Isolate creation
     * @since 3.0
     */
    val total_allocated_bytes: Int
}