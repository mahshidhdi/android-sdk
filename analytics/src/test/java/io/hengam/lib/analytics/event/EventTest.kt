package io.hengam.lib.analytics.event

import org.junit.Assert.assertNull
import org.junit.Test

class EventTest {

    @Test
    fun eventBuilder_buildsEventsWithDefaultValuesIfNotSet(){
        val event = Event.Builder("sampleEvent").build()
        assert(event.action == EventAction.CUSTOM)

        val event2 = Event.Builder("sampleEvent2")
            .setAction(EventAction.ACHIEVEMENT)
            .build()
        assert(event2.action == EventAction.ACHIEVEMENT)
    }

    @Test
    fun ecommerceBuilder_buildsEcommerceWithDefaultValuesIfNotSet(){
        val ecommerce = Ecommerce.Builder("sampleEcommerce", 255.5).build()
        assertNull(ecommerce.category)
        assertNull(ecommerce.quantity)

        val ecommerce2 = Ecommerce.Builder("sampleEcommerce2", 255.5)
            .setCategory("clothes")
            .setQuantity(10)
            .build()
        assert(ecommerce2.category == "clothes")
        assert(ecommerce2.quantity == 10L)
    }
}
