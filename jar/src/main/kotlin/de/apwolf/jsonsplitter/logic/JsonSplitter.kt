package de.apwolf.jsonsplitter.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.logging.log4j.kotlin.Logging
import java.io.File

const val MAXIMUM_PARTS_FOR_WARN = 50L
const val MAXIMUM_PARTS_FOR_ERROR = 500L
const val MAXIMUM_INPUT_FILE_SIZE_IN_BYTES_FOR_ERROR = 1024 * 1024 * 1024L

class JsonSplitter : Logging {

    private val mapper = jacksonObjectMapper()

    private lateinit var jsonList: List<JsonNode>
    private lateinit var file: File
    private var estimatedParts: Int = 0
    private var maxFileSizeInBytes: Long = 0

    init {
        mapper.registerKotlinModule()
    }

    fun configureMaxFileSize(maxFileSizeInBytes: Long): ConfigureSizeResult {
        this.maxFileSizeInBytes = maxFileSizeInBytes
        estimatedParts = calculateNumberOfParts(maxFileSizeInBytes, file, jsonList)
        logger.info("Estimated number of parts: $estimatedParts")
        return ConfigureSizeResult(estimatedParts)
    }

    fun validateFile(path: String): ValidationResult {
        file = File(path)
        if (!file.canRead()) {
            logger.error("No rights to read file $path")
            return ValidationResult(false)
        }
        if (file.length() == 0L) {
            logger.error("File $path is empty")
            return ValidationResult(false)
        }
        if (file.length() > MAXIMUM_INPUT_FILE_SIZE_IN_BYTES_FOR_ERROR) {
            // We don't really have a size limit as long as we keep the heap limit high
            // - current maximum will probably be that we use ArrayLists which us an Int index
            logger.error(
                "File $path is too big, currently supporting max. 1GB ($MAXIMUM_INPUT_FILE_SIZE_IN_BYTES_FOR_ERROR bytes)"
            )
            return ValidationResult(false)
        }

        try {
            jsonList = mapper.readValue(file)
            logger.info("Found JSON list with ${jsonList.size} elements")
        } catch (e: Error) { // We even catch errors to catch heap overflows
            logger.error("Error when parsing file, not a valid JSON string, not a list or heap overflow", e)
            return ValidationResult(false)
        }
        logger.info("Validation ok")

        return ValidationResult(true, estimatedParts)
    }

    fun split(): SplitResult {
        var writtenElements = 0
        var fileIndex = 1
        var currentFile = buildFile(fileIndex)
        var subList = arrayListOf<JsonNode>()
        var currentSize = 4 // 4 bytes for array symboles ("[  ]")
        for (i in jsonList.indices) {
            val nextValueString = jsonList[i].toPrettyString()
            val nextValueSize = nextValueString.toByteArray().size
            if (nextValueSize + currentSize < maxFileSizeInBytes) {
                // nextValue fits in
                subList.add(jsonList[i])
                currentSize += nextValueSize + 2 // 2 bytes for comma and space
            } else {
                // file big enough, time for the next one
                if (subList.isEmpty()) {
                    // Current object does not fit in but list is still empty - object too big
                    logger.error("Object no. $i is bigger than maximum file size, choose a bigger maximum file size!")
                    logger.error("Relevant object: $nextValueString")
                    return SplitResult(false)
                }

                currentSize = nextValueSize
                fileIndex++
                mapper.writerWithDefaultPrettyPrinter().writeValue(currentFile, subList)
                logger.info("Wrote file $currentFile with ${subList.size} elements")
                writtenElements += subList.size
                currentFile = buildFile(fileIndex)
                subList = arrayListOf(jsonList[i]) // so we don't forget it
            }
        }
        if (subList.isNotEmpty()) {
            // write one last file for the rest
            mapper.writerWithDefaultPrettyPrinter().writeValue(currentFile, subList)
            logger.info("Wrote file $currentFile with ${subList.size} elements")
            writtenElements += subList.size
            logger.info("Wrote $writtenElements elements in $fileIndex files")
            return SplitResult(true, fileIndex, estimatedParts, writtenElements, jsonList.size)
        }
        logger.error("No more elements to write but last subList is not empty - wat?")
        return SplitResult(false)
    }

    /**
     * Checks the directory of the given file if a file with the same name and "-1" concatenated exists
     */
    fun checkForExistingSplitFiles(file: File): Boolean {
        return file.parentFile.walkTopDown().maxDepth(1).any {
            it.name.contains(file.nameWithoutExtension + "-1")
        }
    }

    private fun buildFile(fileIndex: Int): File {
        return File(file.parent, file.nameWithoutExtension + "-$fileIndex.json")
    }

    /**
     * Calculating the number of parts is surprisingly difficult. The main problem why you get unexpected results is
     * because of the used indentation. If your source file uses 4 spaces for every element in the array but the
     * split files only use 2 spaces, you save a lot of space
     */
    internal fun calculateNumberOfParts(maxFileSizeInBytes: Long, file: File, jsonList: List<JsonNode>): Int {
        val div: Int = file.length().div(maxFileSizeInBytes).toInt()
        val numberOfPartsByMaxFileSize = if (file.length().rem(maxFileSizeInBytes) > 0) {
            div + 1
        } else {
            div
        }

        // Currently we use numberOfPartsByMaxFileSize
        // val elementsPerList = jsonList.size.div(numberOfPartsByMaxFileSize)
        // val partsByElements = ceil(jsonList.size.toDouble().div(elementsPerList)).toInt()
        return numberOfPartsByMaxFileSize
    }
}

data class ValidationResult(val valid: Boolean, val estimatedParts: Int? = null)

data class SplitResult(
    val success: Boolean, val writtenFiles: Int = 0, val expectedFiles: Int = 0, val writtenObjects: Int = 0,
    val expectedObjects: Int = 0)

data class ConfigureSizeResult(val estimatedParts: Int)