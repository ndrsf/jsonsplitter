package de.apwolf.jsonsplitter

import de.apwolf.jsonsplitter.gui.MainFrame

open class Main {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val gui = MainFrame()
            gui.build()
        }

    }
}