package de.apwolf.jsonsplitter.gui

import de.apwolf.jsonsplitter.logic.JsonSplitter
import de.apwolf.jsonsplitter.logic.MAXIMUM_PARTS_FOR_ERROR
import de.apwolf.jsonsplitter.logic.MAXIMUM_PARTS_FOR_WARN
import de.apwolf.jsonsplitter.logic.SplitResult
import net.miginfocom.swing.MigLayout
import org.apache.logging.log4j.kotlin.Logging
import java.awt.Color
import java.io.File
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileFilter

class MainFrame : Logging {

    private lateinit var jsonSplitter: JsonSplitter

    private lateinit var path: String

    private var estimatedParts = 0

    private val estimatedPartsLabel = JLabel()

    private val fileSizeSelector = JComboBox(arrayOf("1", "5", "10", "50", "100", "200", "500"))

    private val defaultForegroundColor = estimatedPartsLabel.foreground

    private val splitButton = JButton("Split")
    private val statusArea = JTextArea(10, 0)

    private val writtenElementsLabel = JLabel()
    private val writtenFilesLabel = JLabel()
    private val overallResultLabel = JLabel()
    private val openOutputDirButton = JButton("Open output directory")

    fun build() {
        logger.info("Building Swing GUI") // funny enough logger is initialized after first log entry, so...
        LOGGER_SINGLETON?.textArea = statusArea
        jsonSplitter = JsonSplitter()

        val mainFrame = JFrame("JSON Splitter")
        mainFrame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        mainFrame.setSize(800, 600)
        mainFrame.setLocationRelativeTo(null)

        val mainPanel = JPanel(MigLayout(""))

        mainPanel.add(buildInputFilePanel(mainFrame), "growx, pushx, wrap")
        mainPanel.add(buildConfigurationPanel(), "growx, pushx, wrap")
        mainPanel.add(buildGoPanel(), "center, wrap")
        mainPanel.add(buildResultPanel(), "growx, pushx, wrap")
        mainPanel.add(buildLogPanel(), "growx, pushx, wrap")

        mainFrame.contentPane.add(mainPanel)

        //        mainFrame.pack()
        mainFrame.isVisible = true
    }

    private fun buildInputFilePanel(mainFrame: JFrame): JPanel {
        val inputFilePanel = JPanel(MigLayout(""))
        val existingFilesWarningLabel = JLabel("Found old split files which will be overwritten if you continue.")
        existingFilesWarningLabel.foreground = Color.RED

        val inputFileChooser = JFileChooser()
        inputFileChooser.currentDirectory = File(System.getProperty("user.home", "."))
        val jsonFilter = object : FileFilter() {
            override fun accept(f: File): Boolean {
                return f.extension == "json" || f.isDirectory
            }

            override fun getDescription(): String {
                return ".json"
            }
        }
        val allFilter = object : FileFilter() {
            override fun accept(f: File): Boolean {
                return true
            }

            override fun getDescription(): String {
                return "All files"
            }
        }
        inputFileChooser.isAcceptAllFileFilterUsed = false
        inputFileChooser.addChoosableFileFilter(jsonFilter)
        inputFileChooser.addChoosableFileFilter(allFilter) // to have a nice ordering

        val inputFileTextField = JTextField()
        inputFileTextField.isEditable = false
        val inputFileChooseButton = JButton("Choose...")
        inputFileChooseButton.addActionListener {
            val response = inputFileChooser.showDialog(mainFrame, "Choose JSON file")
            if (response == JFileChooser.APPROVE_OPTION) {
                inputFileTextField.text = inputFileChooser.selectedFile.absolutePath
                path = inputFileChooser.selectedFile.absolutePath
                existingFilesWarningLabel.isVisible =
                    jsonSplitter.checkForExistingSplitFiles(inputFileChooser.selectedFile)
                validateFileInput()
                resetResultPanel()
                openOutputDirButton.isEnabled = true
            }
        }

        inputFilePanel.add(JLabel("Input file: "))
        inputFilePanel.add(inputFileTextField, "growx, pushx")
        inputFilePanel.add(inputFileChooseButton, "wrap")
        inputFilePanel.add(existingFilesWarningLabel, "span 3, wrap")
        existingFilesWarningLabel.isVisible = false

        return inputFilePanel
    }

    private fun configureMaxFileSize() {
        val response =
            jsonSplitter.configureMaxFileSize(fileSizeSelector.selectedItem!!.toString().toLong() * 1024 * 1024)

        estimatedParts = response.estimatedParts
        estimatedPartsLabel.text = estimatedParts.toString()
        when (estimatedParts) {
            0 -> {
                logger.error("Estimated 0 parts, disabling split")
                splitButton.isEnabled = false
            }

            in 1 until MAXIMUM_PARTS_FOR_WARN -> {
                estimatedPartsLabel.foreground = defaultForegroundColor
                splitButton.isEnabled = true
            }

            in MAXIMUM_PARTS_FOR_WARN until MAXIMUM_PARTS_FOR_ERROR -> {
                logger.warn("Estimated a big amount of parts ($estimatedParts)")
                estimatedPartsLabel.foreground = Color.ORANGE
                splitButton.isEnabled = true
            }

            in MAXIMUM_PARTS_FOR_ERROR until Long.MAX_VALUE -> {
                logger.error(
                    "Estimated a huge amount of parts ($estimatedParts), disabling split. Choose a bigger file size!"
                )
                estimatedPartsLabel.foreground = Color.RED
                splitButton.isEnabled = false
            }
        }

    }

    private fun buildConfigurationPanel(): JPanel {
        val configurationPanel = JPanel(MigLayout())

        // TODO make it editable and add a DocumentFilter which removes forbidden (!= int) chars
        // TODO disable sizes bigger than file itself
        fileSizeSelector.isEditable = false
        fileSizeSelector.addActionListener { configureMaxFileSize() }

        configurationPanel.add(JLabel("Select file size in MB:"))
        configurationPanel.add(fileSizeSelector, "wrap")

        configurationPanel.add(JLabel("Estimated number of parts: "))
        configurationPanel.add(estimatedPartsLabel)

        configurationPanel.border = TitledBorder("Configuration")

        return configurationPanel
    }

    private fun buildLogPanel(): JPanel {
        val logPanel = JPanel(MigLayout())

        statusArea.isEditable = false
        val statusAreaScrollable = JScrollPane(statusArea)

        logPanel.add(statusAreaScrollable, "growx, pushx")
        logPanel.border = TitledBorder("Logs")

        return logPanel
    }

    private fun buildGoPanel(): JPanel {
        val goPanel = JPanel(MigLayout())
        splitButton.isEnabled = false

        splitButton.addActionListener {
            val result = jsonSplitter.split()
            validateResult(result)
        }

        goPanel.add(splitButton, "pushx, growx, center, wrap")

        return goPanel
    }

    private fun buildResultPanel(): JPanel {
        val resultPanel = JPanel(MigLayout())

        openOutputDirButton.isEnabled = false
        openOutputDirButton.addActionListener {
            Runtime.getRuntime().exec("explorer.exe /select, $path")
        }

        resultPanel.add(writtenElementsLabel)
        resultPanel.add(writtenFilesLabel, "wrap")
        resultPanel.add(overallResultLabel, "span 2, wrap")
        resultPanel.add(openOutputDirButton)

        resultPanel.border = TitledBorder("Result")

        resetResultPanel()

        return resultPanel
    }

    private fun validateResult(result: SplitResult) {
        writtenElementsLabel.text = "Written elements: ${result.writtenObjects}/${result.expectedObjects}"
        writtenFilesLabel.text = "Written files: ${result.writtenFiles}/${result.expectedFiles}"

        if (result.writtenObjects == result.expectedObjects) {
            writtenElementsLabel.foreground = defaultForegroundColor
            overallResultLabel.foreground = defaultForegroundColor
            if (result.writtenFiles == result.expectedFiles) {
                overallResultLabel.text = "Everything went fine"
            } else {
                overallResultLabel.text = "Miscalculated the number of files, but output is OK"
            }
        } else {
            writtenElementsLabel.foreground = Color.RED
            overallResultLabel.foreground = Color.RED
            overallResultLabel.text = "ERROR: Did not write all elements, disregard the output and try again!"

        }
    }

    private fun resetResultPanel() {
        writtenElementsLabel.foreground = defaultForegroundColor
        overallResultLabel.foreground = defaultForegroundColor
        overallResultLabel.text = "Waiting for input"
        writtenElementsLabel.text = " "
        writtenFilesLabel.text = " "

    }

    private fun validateFileInput() {
        val response =
            jsonSplitter.validateFile(path)
        if (response.valid) {
            configureMaxFileSize()
        } else {
            estimatedPartsLabel.text = ""
            splitButton.isEnabled = false
        }
    }

}
