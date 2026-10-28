package com.example.jkword.editor

import com.example.jkword.model.DocumentModel

/**
 * Robust Undo/Redo manager for the document editor.
 * Maintains undo/redo history stacks with limit to preserve memory.
 */
class UndoRedoManager(private val maxHistory: Int = 40) {
    private val undoStack = mutableListOf<DocumentModel>()
    private val redoStack = mutableListOf<DocumentModel>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun pushState(currentState: DocumentModel) {
        undoStack.add(currentState)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(currentState: DocumentModel): DocumentModel? {
        if (!canUndo) return null
        val previousState = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(currentState)
        return previousState
    }

    fun redo(currentState: DocumentModel): DocumentModel? {
        if (!canRedo) return null
        val nextState = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(currentState)
        return nextState
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
