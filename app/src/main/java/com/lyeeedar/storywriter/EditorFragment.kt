package com.lyeeedar.storywriter

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.lyeeedar.Util.Future
import kotlinx.android.synthetic.main.editor_fragment.*
import org.languagetool.JLanguageTool
import org.languagetool.language.BritishEnglish

class EditorFragment : Fragment() {
    var disableUndo = false
    var currentChange: TextChange? = null
    var book: Book = Book("Example")
        set(value) {
            if (field != value) {
                field = value

                chapter = book.chapters[0]
            }
        }

    var chapter: Chapter = Chapter()
        set(value) {
            field = value

            disableUndo = true
            updateText()
            chapter_title.setText(chapter.fullTitle)
            disableUndo = false

            updateUndoRedoButtons()
        }

    val spellCheckerThread = SpellCheckerThread(this)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        spellCheckerThread.start()

        return inflater.inflate(R.layout.editor_fragment, container, false)
    }

    fun updateUndoRedoButtons() {
        undoButton.isEnabled = chapter.textUndoStack.size > 0
        redoButton.isEnabled = chapter.textRedoStack.size > 0

        undoButton.background = ColorDrawable(if (undoButton.isEnabled) resources.getColor(R.color.button) else resources.getColor(R.color.buttonDisabled))
        redoButton.background = ColorDrawable(if (redoButton.isEnabled) resources.getColor(R.color.button) else resources.getColor(R.color.buttonDisabled))
    }

    var ignoreUpdate = false
    fun updateText() {
        val cursorPos = text_editor.selectionStart
        val scrollStart = text_editor.scrollY

        ignoreUpdate = true
        text_editor.setText(Html.fromHtml(chapter.getAnnotatedText()))
        ignoreUpdate = false

        text_editor.setSelection(Math.min(cursorPos, text_editor.text.length-1))
        text_editor.scrollY = scrollStart
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        book = (activity as MainActivity).viewModel.book

        updateUndoRedoButtons()

        chapter_title.setOnClickListener {
            val popupMenu = PopupMenu(context, chapter_title)

            var i = 0
            for (chapter in book.chapters) {
                popupMenu.menu.add(0, i, i, chapter.fullTitle)
                i++
            }

            popupMenu.setOnMenuItemClickListener { item ->
                chapter = book.chapters[item.itemId]
                false
            }
            popupMenu.show()
        }

        undoButton.setOnClickListener {
            Future.cancel(text_editor)

            chapter.undo()

            disableUndo = true
            updateText()
            disableUndo = false

            updateUndoRedoButtons()
        }

        redoButton.setOnClickListener {
            Future.cancel(text_editor)

            chapter.redo()

            disableUndo = true
            updateText()
            disableUndo = false

            updateUndoRedoButtons()
        }

        text_editor.addTextChangedListener(object : TextChangedListener<EditText>(text_editor) {
            override fun onTextChanged(target: EditText, s: Editable?) {
                if (ignoreUpdate) return

                synchronized(chapter) {
                    chapter.hasUnflushedText = true

                    val oldtext = chapter.rawText
                    val newtext = target.text.toString()
                    chapter.rawText = newtext

                    Future.call({
                        synchronized(chapter) {
                            chapter.flushRawText()
                        }
                    }, 0.5f, "flush")

                    if (!disableUndo) {
                        if (currentChange == null) {
                            currentChange = TextChange(oldtext, newtext)
                        } else {
                            currentChange!!.after = newtext
                        }

                        Future.call({
                            val currentChange = currentChange
                            if (currentChange != null) chapter.textUndoStack.add(currentChange)

                            (context as Activity).runOnUiThread {
                                updateUndoRedoButtons()
                            }

                        }, 0.5f, "undoredo")

                        chapter.textRedoStack.clear()

                        updateUndoRedoButtons()
                    }
                }
            }
        })
    }
}

abstract class TextChangedListener<T>(private val target: T) : TextWatcher {
    override fun beforeTextChanged(
        s: CharSequence,
        start: Int,
        count: Int,
        after: Int
    ) {
    }

    override fun onTextChanged(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int
    ) {
    }

    override fun afterTextChanged(s: Editable) {
        this.onTextChanged(target, s)
    }

    abstract fun onTextChanged(target: T, s: Editable?)

}

class SpellCheckerThread(val editor: EditorFragment) : Thread() {
    @Synchronized
    override fun run() {
        val languageTool = JLanguageTool(BritishEnglish())
        var index = 0

        var time = System.currentTimeMillis()
        while (true) {
            val currentTime = System.currentTimeMillis()
            time = currentTime

            var paragraph: Paragraph? = null
            synchronized(editor.chapter) {
                if (!editor.chapter.hasUnflushedText) {
                    if (index >= editor.chapter.paragraphs.size) {
                        index = 0
                    }

                    paragraph = editor.chapter.paragraphs.get(index)
                }
            }

            val didWork = paragraph?.doSpellCheck(languageTool) ?: false

            synchronized(editor.chapter) {
                if (didWork && !editor.chapter.hasUnflushedText) {
                    (editor.context as Activity).runOnUiThread {
                        editor.updateText()
                    }
                }
            }

            index++

            Thread.yield()
            Thread.sleep(100)
            Thread.yield()
        }
    }
}