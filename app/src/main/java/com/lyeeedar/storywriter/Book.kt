package com.lyeeedar.storywriter

import org.languagetool.JLanguageTool
import org.languagetool.rules.RuleMatch

class Book(val title: String)
{
    val chapters = ArrayList<Chapter>()
}

class TextChange(var before: String, var after: String)

class Chapter
{
    var index: Int = 1
    var title: String = ""

    val paragraphs = ArrayList<Paragraph>()

    val textUndoStack = ArrayList<TextChange>()
    val textRedoStack = ArrayList<TextChange>()

    val fullTitle: String
        get() = "${index}. $title"

    var rawText: String = ""

    fun flushRawText() {
        setAnnotatedText(rawText)
    }

    fun getAnnotatedText(): String {

        val output = StringBuilder()

        for (paragraph in paragraphs) {
            output.append(paragraph.spellCheckedAnnotatedText)
            output.append("\n\n")
        }

        rawText = output.toString()
        return rawText
    }

    fun setAnnotatedText(text: String) {

        val paragraphMap = HashMap<Int, Paragraph>()
        for (paragraph in paragraphs) {
            paragraphMap.put(paragraph.spellCheckedAnnotatedText.hashCode(), paragraph)
        }
        paragraphs.clear()

        val textParagraphs = text.split('\n')
        for (textParagraph in textParagraphs) {
            if (textParagraph.isBlank()) continue

            var paragraph = paragraphMap.get(textParagraph.hashCode())
            if (paragraph == null) {
                paragraph = Paragraph()
                paragraph.setAnnotatedText(textParagraph)
            }

            paragraphs.add(paragraph)
        }
    }

    fun undo() {
        val text = textUndoStack[textUndoStack.size-1]
        textUndoStack.removeAt(textUndoStack.size-1)

        textRedoStack.add(text)

        setAnnotatedText(text.before)
    }

    fun redo() {
        val text = textRedoStack[textRedoStack.size-1]
        textRedoStack.removeAt(textRedoStack.size-1)

        textUndoStack.add(text)

        setAnnotatedText(text.after)
    }
}

class ErrorRegion(var start: Int, var end: Int, var message: String)
{
    val subregions = ArrayList<ErrorRegion>()
}
class Paragraph
{
    var text: String = ""
        set(value) {
            field = value

            if (spellCheckedHash != field.hashCode()) {
                spellCheckedAnnotatedText = field
            }
        }

    var spellCheckedHash = 0
    var spellCheckedAnnotatedText: String = ""
    val spellcheckResults = ArrayList<RuleMatch>()

    fun doSpellCheck(languageTool: JLanguageTool) {
        val textHash = text.hashCode()
        if (spellCheckedHash != textHash) {
            spellCheckedHash = textHash
            spellcheckResults.clear()
            spellcheckResults.addAll(languageTool.check(text))
            spellCheckedAnnotatedText = getAnnotatedText()
        }
    }

    fun getAnnotatedText(): String {
        val rawText = text
        val regions = ArrayList<ErrorRegion>()

        for (error in spellcheckResults) {
            val newregion = ErrorRegion(error.fromPos, error.toPos, error.message)
            var addRegion = true

            val itr = regions.iterator()
            while (itr.hasNext()) {
                val region = itr.next()

                // contained completely within
                if (region.start <= error.fromPos && region.end >= error.toPos) {
                    addRegion = false
                    region.subregions.add(newregion)
                }
                // contains
                else if (error.fromPos <= region.start && error.toPos >= region.end) {
                    itr.remove()
                    newregion.subregions.add(region)
                }
                // overlaps
                else if (error.fromPos >= region.start && error.toPos <= region.end) {
                    addRegion = false
                    region.start = Math.min(error.fromPos, region.start)
                    region.end = Math.min(error.toPos, region.end)
                    region.subregions.add(region)
                }
            }

            if (addRegion) {
                regions.add(newregion)
            }
        }

        regions.sortByDescending { it.start }

        var workingText = rawText
        val finalTextParts = ArrayList<String>()
        for (region in regions) {
            val textAfter = workingText.substring(region.end)
            val textInside = workingText.substring(region.start, region.end)

            finalTextParts.add(textAfter)
            finalTextParts.add(errorBlockEnd)
            finalTextParts.add(textInside)
            finalTextParts.add(errorBlockStart)

            workingText = workingText.substring(0, region.start)
        }

        val finalText = StringBuilder()
        for (text in finalTextParts.reversed()) {
            finalText.append(text)
        }

        return finalText.toString()
    }

    fun setAnnotatedText(text: String) {
        val rawText = text.replace(errorBlockStart, "").replace(errorBlockEnd, "")
        this.text = rawText
    }

    companion object
    {
        val errorBlockStart = "<font color=\"red\">"
        val errorBlockEnd = "</font>"
    }
}