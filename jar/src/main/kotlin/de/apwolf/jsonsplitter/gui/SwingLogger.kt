package de.apwolf.jsonsplitter.gui

import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Core
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout
import javax.swing.JTextArea

var LOGGER_SINGLETON: SwingLogger? = null

@Plugin(name = "JTextAreaAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
class SwingLogger(name: String, filter: Filter?, pattern: PatternLayout? = createDefaultLayout()) :
    AbstractAppender(name, filter, pattern) {

    var textArea: JTextArea? = null

    companion object {

        @JvmStatic
        @PluginFactory
        fun createAppender(
            @PluginAttribute("name") name: String, @PluginElement("Filter") filter: Filter?,
            @PluginElement("pattern") pattern: PatternLayout
        ): SwingLogger {
            val instance = SwingLogger(name, filter, pattern)
            LOGGER_SINGLETON = instance
            return instance
        }

    }

    override fun append(event: LogEvent) {
        if (textArea != null) {
            // We are in the AWT (yes this is bad) so no reason for invokeLater
            textArea!!.append(String(this.layout.toByteArray(event)))
            textArea!!.caretPosition = textArea!!.document.length; // "autoscroll"
        }

    }

}