package io.hengam.lib.internal

class ComponentNotAvailableException(component: String) : HengamException("Could not obtain Hengam component $component")