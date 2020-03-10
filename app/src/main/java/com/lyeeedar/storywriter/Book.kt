package com.lyeeedar.storywriter

import android.content.Context
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.languagetool.JLanguageTool
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class Book(var title: String)
{
    val chapters = ArrayList<Chapter>()

    private val backupFileName = "$title-backup.bak"

    fun loadBackup(context: Context) {
        var input: Input? = null
        try
        {
            input = Input(GZIPInputStream(context.openFileInput(backupFileName)))
            load(kryo, input)
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            context.deleteFile(backupFileName)
        }
        finally
        {
            input?.close()
        }
    }

    fun backup(context: Context) {
        var output: Output? = null
        try
        {
            output = Output(GZIPOutputStream(context.openFileOutput(backupFileName, Context.MODE_PRIVATE)))
            save(kryo, output)
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
        finally
        {
            output?.close()
        }
    }

    fun save(kryo: Kryo, output: Output) {
        output.writeString(title)
        output.writeInt(chapters.size, true)
        for (chapter in chapters) {
            synchronized(chapter) {
                chapter.save(kryo, output)
            }
        }
    }

    fun load(kryo: Kryo, input: Input) {
        title = input.readString()
        chapters.clear()

        val numChapters = input.readInt(true)
        for (i in 0 until numChapters) {
            val chapter = Chapter()
            chapter.load(kryo, input)

            chapters.add(chapter)
        }
    }
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

    var hasUnflushedText = false
    var rawText: String = ""

    fun flushRawText() {
        setAnnotatedText(rawText)
        hasUnflushedText = false
    }

    fun getAnnotatedText(): String {

        val annotated = StringBuilder()
        val raw = StringBuilder()

        for (paragraph in paragraphs) {
            annotated.append(paragraph.spellCheckedAnnotatedText)
            annotated.append("<br />")

            raw.append(paragraph.text)
            raw.append("\n")
        }

        rawText = raw.toString()
        return annotated.toString()
    }

    fun setAnnotatedText(text: String) {

        val paragraphMap = HashMap<Int, Paragraph>()
        for (paragraph in paragraphs) {
            paragraphMap.put(paragraph.text.trim().hashCode(), paragraph)
        }
        paragraphs.clear()

        val textParagraphs = text.split("\n")
        for (textParagraph in textParagraphs) {
            if (textParagraph.isBlank()) continue

            var paragraph = paragraphMap.get(textParagraph.trim().hashCode())
            if (paragraph == null) {
                paragraph = Paragraph()
                paragraph.text = textParagraph
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

    fun save(kryo: Kryo, output: Output) {
        output.writeString(title)
        output.writeInt(index)
        output.writeInt(paragraphs.size, true)
        for (paragraph in paragraphs) {
            paragraph.save(kryo, output)
        }
    }

    fun load(kryo: Kryo, input: Input) {
        title = input.readString()
        index = input.readInt()

        val raw = StringBuilder()

        val numParagraphs = input.readInt(true)
        for (i in 0 until numParagraphs) {
            val paragraph = Paragraph()
            paragraph.load(kryo, input)

            paragraphs.add(paragraph)

            raw.append(paragraph.text)
            raw.append("\n")
        }

        rawText = raw.toString()
    }
}

class ErrorRegion()
{
    var start: Int = 0
    var end: Int = 0
    var message: String = ""

    constructor(start: Int, end: Int, message: String) : this()
    {
        this.start = start
        this.end = end
        this.message = message
    }

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
    val spellcheckResults = ArrayList<ErrorRegion>()

    fun doSpellCheck(languageTool: JLanguageTool): Boolean {
        val textHash = text.hashCode()
        if (spellCheckedHash != textHash) {
            spellCheckedHash = textHash

            val errors = languageTool.check(text)
            val regions = ArrayList<ErrorRegion>()

            for (error in errors) {
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

            spellcheckResults.clear()
            spellcheckResults.addAll(regions)

            spellCheckedAnnotatedText = getAnnotatedText()

            return true
        }

        return false
    }

    fun getAnnotatedText(): String {
        val rawText = text

        var workingText = rawText
        val finalTextParts = ArrayList<String>()
        for (region in spellcheckResults) {
            val textAfter = workingText.substring(region.end)
            val textInside = workingText.substring(region.start, region.end)

            finalTextParts.add(textAfter)
            finalTextParts.add(errorBlockEnd)
            finalTextParts.add(textInside)
            finalTextParts.add(errorBlockStart)

            workingText = workingText.substring(0, region.start)
        }
        finalTextParts.add(workingText)

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

    fun save(kryo: Kryo, output: Output) {
        output.writeString(text)
        output.writeInt(spellCheckedHash)
        output.writeString(spellCheckedAnnotatedText)
        output.writeInt(spellcheckResults.size, true)
        for (result in spellcheckResults) {
            kryo.writeObject(output, result)
        }
    }

    fun load(kryo: Kryo, input: Input) {
        text = input.readString()
        spellCheckedHash = input.readInt()
        spellCheckedAnnotatedText = input.readString()
        val numResults = input.readInt(true)
        for (i in 0 until numResults) {
            val result = kryo.readObject(input, ErrorRegion::class.java)
            spellcheckResults.add(result)
        }
    }

    companion object
    {
        val errorBlockStart = "<font color=\"red\">"
        val errorBlockEnd = "</font>"
    }
}