package co.pushe.plus.internal

class ComponentNotAvailableException(component: String) : PusheException("Could not obtain Pushe component $component")