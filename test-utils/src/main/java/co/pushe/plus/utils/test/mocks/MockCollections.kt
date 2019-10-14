package co.pushe.plus.utils.test.mocks

import co.pushe.plus.utils.PersistedList
import co.pushe.plus.utils.PersistedMap
import co.pushe.plus.utils.PersistedSet
import co.pushe.plus.utils.Time

class MockPersistedSet<T>(private val innerSet: MutableSet<T> = mutableSetOf())
    : MutableSet<T> by innerSet, PersistedSet<T> {
    override fun save() {}
}

class MockPersistedList<T>(private val innerList: MutableList<T> = mutableListOf())
    : MutableList<T> by innerList, PersistedList<T> {
    override fun save() {}
}

class MockPersistedMap<T>(private val innerMap: MutableMap<String, T> = mutableMapOf())
    : MutableMap<String, T> by innerMap, PersistedMap<T> {
    override fun put(key: String, value: T, expirationTime: Time?): T? = value
    override fun save() {}
}