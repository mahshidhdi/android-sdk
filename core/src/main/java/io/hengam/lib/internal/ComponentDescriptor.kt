package io.hengam.lib.internal

class ComponentDescriptor (
        val name: String,
        val initializerClass: String,
        val dependencies: List<String> = emptyList()
)