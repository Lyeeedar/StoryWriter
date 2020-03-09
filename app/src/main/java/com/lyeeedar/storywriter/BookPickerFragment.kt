package com.lyeeedar.storywriter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.book_picker_fragment.*
import java.io.File
import java.io.IOException


class BookPickerFragment : Fragment()
{
    private val bookSources = ArrayList<BookSource>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.book_picker_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findBooks()

        book_sources.adapter = BookSourceAdapter(context!!, this, bookSources)
    }

    private val READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1
    private fun findBooks() {
        if (ContextCompat.checkSelfPermission(context!!, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE_PERMISSION_CODE)
        }

        searchDiskFolder("Download")
        searchDiskFolder("Documents")
    }

    private fun searchDiskFolder(name: String) {
        val rootPath = Environment.getExternalStorageDirectory().toString() + "/" + name

        val bookSource = BookSource(name, rootPath, ArrayList())

        val rootFolder = File(rootPath)
        searchFolder(rootFolder, bookSource)

        if (bookSource.files.size > 0) bookSources.add(bookSource)
    }

    private fun searchFolder(folder: File, source: BookSource) {
        for (file in folder.listFiles()) {
           if (file.isDirectory) {
               searchFolder(file, source)
           } else if (file.extension == "txt") {
               source.files.add(file.absolutePath)
           }
        }
    }

    fun loadBook(source: BookSource, path: String) {
        (activity as MainActivity).viewModel.book = source.load(path)

        findNavController().navigate(R.id.action_BookPickerFragment_to_EditorFragment)
    }
}

class BookSource(val sourceName: String, val rootPath: String, val files: ArrayList<String>) {
    fun load(path: String): Book {
        val file = File(path)
        if (!file.exists()) throw IOException("File $path does not exist!")

        val contents = file.readLines()

        val book = Book(file.nameWithoutExtension)

        var currentChapter = Chapter()

        val chapterTitleRegex = "([0-9]+)[\\:,\\.] (.*)".toPattern()
        for (line in contents) {
            val matcher = chapterTitleRegex.matcher(line.trim())
            if (matcher.matches()) {
                val result = matcher.toMatchResult()
                val num = result.group(1)
                val title = result.group(2)

                currentChapter = Chapter()
                currentChapter.index = num.toInt()
                currentChapter.title = title
                book.chapters.add(currentChapter)
            } else {
                if (currentChapter.text.isNotEmpty()) currentChapter.text += "\n"
                currentChapter.text += line
            }
        }

        return book
    }
}

class BookSourceAdapter(context: Context, val bookPicker: BookPickerFragment, val values: ArrayList<BookSource>) : ArrayAdapter<BookSource>(context, -1, values) {
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val source = values[position]

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView: View = inflater.inflate(R.layout.book_source, parent, false)

        val title = rowView.findViewById<View>(R.id.label) as TextView
        val list = rowView.findViewById<View>(R.id.list) as ListView
        list.adapter = BookSourceItemAdapter(context, source, bookPicker, source.files)

        title.text = source.sourceName

        return rowView
    }
}

class BookSourceItemAdapter(context: Context, val bookSource: BookSource, val bookPicker: BookPickerFragment, val values: ArrayList<String>) : ArrayAdapter<String>(context, -1, values) {
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val file = values[position]

        val rowView = TextView(context)

        val relPath = file.replace(bookSource.rootPath + "/", "")

        rowView.text = relPath
        rowView.setOnClickListener {
            bookPicker.loadBook(bookSource, file)
        }

        return rowView
    }
}