package com.lyeeedar.storywriter

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.lyeeedar.Util.Future
import kotlinx.android.synthetic.main.editor_fragment.*

class TextChange(var before: String, var after: String)

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
            text_editor.setText(chapter.text)
            chapter_title.setText(chapter.fullTitle)
            disableUndo = false

            updateUndoRedoButtons()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.editor_fragment, container, false)
    }

    fun updateUndoRedoButtons() {
        undoButton.isEnabled = chapter.textUndoStack.size > 0
        redoButton.isEnabled = chapter.textRedoStack.size > 0

        undoButton.background = ColorDrawable(if (undoButton.isEnabled) resources.getColor(R.color.button) else resources.getColor(R.color.buttonDisabled))
        redoButton.background = ColorDrawable(if (redoButton.isEnabled) resources.getColor(R.color.button) else resources.getColor(R.color.buttonDisabled))
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
            val cursorPos = text_editor.selectionStart
            text_editor.setText(chapter.text)
            text_editor.setSelection(cursorPos)
            disableUndo = false

            updateUndoRedoButtons()
        }

        redoButton.setOnClickListener {
            Future.cancel(text_editor)

            chapter.redo()

            disableUndo = true
            val cursorPos = text_editor.selectionStart
            text_editor.setText(chapter.text)
            text_editor.setSelection(cursorPos)
            disableUndo = false

            updateUndoRedoButtons()
        }

        text_editor.addTextChangedListener(object : TextChangedListener<EditText>(text_editor) {
            override fun onTextChanged(target: EditText, s: Editable?) {
                val oldtext = chapter.text
                val newtext = target.text.toString()
                chapter.text = newtext

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

                    }, 0.5f, text_editor)

                    chapter.textRedoStack.clear()

                    updateUndoRedoButtons()
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