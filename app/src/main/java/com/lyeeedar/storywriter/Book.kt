package com.lyeeedar.storywriter

class Book
{
    val chapters = ArrayList<Chapter>()
}

class Chapter
{
    var index: Int = 1
    var title: String = ""
    var text: String = ""

    val textUndoStack = ArrayList<TextChange>()
    val textRedoStack = ArrayList<TextChange>()

    val fullTitle: String
        get() = "${index+1}. $title"

    fun undo() {
        val text = textUndoStack[textUndoStack.size-1]
        textUndoStack.removeAt(textUndoStack.size-1)

        textRedoStack.add(text)

        this.text = text.before
    }

    fun redo() {
        val text = textRedoStack[textRedoStack.size-1]
        textRedoStack.removeAt(textRedoStack.size-1)

        textUndoStack.add(text)

        this.text = text.after
    }
}