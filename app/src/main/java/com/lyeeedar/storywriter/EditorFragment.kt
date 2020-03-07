package com.lyeeedar.storywriter

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.lyeeedar.Util.Future
import kotlinx.android.synthetic.main.editor_fragment.*


class TextChange(var before: String, var after: String)

class EditorFragment : Fragment() {
    val textUndoStack = ArrayList<TextChange>()
    val textRedoStack = ArrayList<TextChange>()
    var disableUndo = false
    var currentChange: TextChange? = null
    var text: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.editor_fragment, container, false)
    }

    fun updateUndoRedoButtons() {
        undoButton.isEnabled = textUndoStack.size > 0
        redoButton.isEnabled = textRedoStack.size > 0

        undoButton.background = ColorDrawable(if (undoButton.isEnabled) resources.getColor(R.color.button) else resources.getColor(R.color.buttonDisabled))
        redoButton.background = ColorDrawable(if (redoButton.isEnabled) resources.getColor(R.color.button) else resources.getColor(R.color.buttonDisabled))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        text = text_editor.text.toString()

        updateUndoRedoButtons()
        //view.findViewById<Button>(R.id.button_first).setOnClickListener {
        //    findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        //}

        undoButton.setOnClickListener {
            Future.cancel(text_editor)

            val text = textUndoStack[textUndoStack.size-1]
            textUndoStack.removeAt(textUndoStack.size-1)

            textRedoStack.add(text)

            disableUndo = true
            val cursorPos = text_editor.selectionStart
            text_editor.setText(text.before)
            text_editor.setSelection(cursorPos)
            disableUndo = false

            updateUndoRedoButtons()
        }

        redoButton.setOnClickListener {
            Future.cancel(text_editor)

            val text = textRedoStack[textRedoStack.size-1]
            textRedoStack.removeAt(textRedoStack.size-1)

            textUndoStack.add(text)

            disableUndo = true
            val cursorPos = text_editor.selectionStart
            text_editor.setText(text.after)
            text_editor.setSelection(cursorPos)
            disableUndo = false

            updateUndoRedoButtons()
        }

        text_editor.addTextChangedListener(object : TextChangedListener<EditText>(text_editor) {
            override fun onTextChanged(target: EditText, s: Editable?) {
                val oldtext = text
                val newtext = target.text.toString()
                text = newtext

                if (!disableUndo) {
                    if (currentChange == null) {
                        currentChange = TextChange(oldtext, newtext)
                    } else {
                        currentChange!!.after = newtext
                    }

                    Future.call({
                        val currentChange = currentChange
                        if (currentChange != null) textUndoStack.add(currentChange)

                        (context as Activity).runOnUiThread {
                            updateUndoRedoButtons()
                        }

                    }, 0.5f, text_editor)

                    textRedoStack.clear()

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