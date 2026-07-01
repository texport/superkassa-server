package kz.mybrain.superkassa.core.config

import io.mockk.every
import io.mockk.mockk
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenApiConfigTest {

    private class DummySubtype

    @Test
    fun testKmpSchemaConverterFallbackForSubtypes() {
        val config = OpenApiConfig()
        val converter = config.kmpSchemaConverter()

        val type = AnnotatedType(DummySubtype::class.java).skipSchemaName(true)
        val context = mockk<ModelConverterContext>()
        val chain = mockk<Iterator<ModelConverter>>()

        // Mock empty chain so it returns null
        every { chain.hasNext() } returns false

        val result = converter.resolve(type, context, chain)

        assertNotNull(result)
        assertEquals("DummySubtype", result.name)
    }
}
