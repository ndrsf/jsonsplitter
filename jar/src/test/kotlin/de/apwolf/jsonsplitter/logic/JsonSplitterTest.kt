package de.apwolf.jsonsplitter.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*

internal class JsonSplitterTest {

    @Test
    // TODO think about this test
    internal fun testCalculateNumberOfParts() {
        val mapper = jacksonObjectMapper()
        mapper.registerKotlinModule()
        val sut = JsonSplitter()

        var file = File("target/test-classes/partcalculation/tiny.json")
        var jsonList = mapper.readValue<List<JsonNode>>(file)
        sut.calculateNumberOfParts(240, file, jsonList)

        file = File("target/test-classes/partcalculation/1MB.json")
        jsonList = mapper.readValue(file)
        sut.calculateNumberOfParts(1024 * 1024, file, jsonList)

        file = File("target/test-classes/partcalculation/5MB.json")
        jsonList = mapper.readValue(file)
        sut.calculateNumberOfParts(1024 * 1024, file, jsonList)

        file = File("target/test-classes/partcalculation/10MB.json")
        jsonList = mapper.readValue(file)
        sut.calculateNumberOfParts(1024 * 1024, file, jsonList)

        file = File("target/test-classes/partcalculation/20MB.json")
        jsonList = mapper.readValue(file)
        sut.calculateNumberOfParts(1024 * 1024, file, jsonList)
    }

    @Test
    fun testCheckForExistingSplitFiles() {
        val sut = JsonSplitter()
        val fileWithExistingSplitFile = File("target/test-classes/existingSplitFiles/test.json")

        assertTrue(fileWithExistingSplitFile.exists())
        assertTrue(sut.checkForExistingSplitFiles(fileWithExistingSplitFile))

        val fileWithoutExistingSplitFile = File("target/test-classes/existingSplitFiles/testtwo.json")

        assertFalse(fileWithoutExistingSplitFile.exists())
        assertFalse(sut.checkForExistingSplitFiles(fileWithoutExistingSplitFile))
    }

    @Test
    @Disabled
    fun createTestJson() {
        val sizeInBytes = 1024 * 1024 * 1000
        val mapper = jacksonObjectMapper()
        mapper.registerKotlinModule()

        val file = File("target/test-classes/1GB.json")

        val elements = arrayListOf<JsonNode>()
        val obj = TestJsonObject(randomString(), randomString(), randomString(), randomString(), randomString(),
            randomString(), randomString(), randomString(), randomString(), randomString())
        val node = mapper.convertValue(obj, JsonNode::class.java)
        val objSize = node.toPrettyString().toByteArray().size

        var currentSize = 0
        while (true) {
            if (currentSize + objSize < sizeInBytes) {
                currentSize += objSize
                elements.add(node)
            } else {
                break
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(file, elements)
    }

    private fun randomString(): String {
        return UUID.randomUUID().toString()
    }

}

data class TestJsonObject(val parameter1: String, val parameter2: String, val parameter3: String,
                          val parameter4: String, val parameter5: String, val parameter6: String,
                          val parameter7: String, val parameter8: String, val parameter9: String,
                          val parameter10: String)