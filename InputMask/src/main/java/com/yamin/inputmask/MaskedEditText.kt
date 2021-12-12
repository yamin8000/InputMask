//Original author https://github.com/egslava
package com.yamin.inputmask

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.widget.TextView.OnEditorActionListener
import com.google.android.material.textfield.TextInputEditText

class MaskedEditText : TextInputEditText, TextWatcher {

    private val onEditorActionListener = OnEditorActionListener { _, _, _ -> true }
    private var maxRawLength = 0
    private lateinit var mask: String
    private var charRepresentation = 0.toChar()
    private var keepHint = false
    private lateinit var rawToMask: IntArray
    private lateinit var rawText: RawText
    private var editingBefore = false
    private var editingOnChanged = false
    private var editingAfter = false
    private lateinit var maskToRaw: IntArray
    private var selectionIndex = 0
    private var initialized = false
    private var ignore = false
    private var lastValidMaskPosition = 0
    private var selectionChanged = false
    private lateinit var focusChangeListener: OnFocusChangeListener
    private lateinit var allowedChars: String
    private lateinit var deniedChars: String
    private var isKeepingText = false

    init {
        addTextChangedListener(this)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.MaskedEditText)
        mask = attributes.getString(R.styleable.MaskedEditText_mask) ?: ""
        allowedChars = attributes.getString(R.styleable.MaskedEditText_allowed_chars) ?: ""
        deniedChars = attributes.getString(R.styleable.MaskedEditText_denied_chars) ?: ""
        val enableImeAction = attributes.getBoolean(R.styleable.MaskedEditText_enable_ime_action, false)
        val representation = attributes.getString(R.styleable.MaskedEditText_char_representation)
        charRepresentation = if (representation == null) CONSTANTS.NUM_SIGN else representation[0]
        keepHint = attributes.getBoolean(R.styleable.MaskedEditText_keep_hint, false)
        cleanUp()
        // Ignoring enter key presses if needed
        if (enableImeAction) setOnEditorActionListener(null)
        else setOnEditorActionListener(onEditorActionListener)
        attributes.recycle()
    }

    private fun cleanUp() {
        initialized = false
        //TODO wtf return
        if (mask.isEmpty()) return
        generatePositionArrays()
        if (!isKeepingText) {
            rawText = RawText()
            selectionIndex = rawToMask[0]
        }
        editingBefore = true
        editingOnChanged = true
        editingAfter = true
        if (hasHint() && rawText.getLength() == 0) this.setText(makeMaskedTextWithHint())
        else this.setText(makeMaskedText())
        editingBefore = false
        editingOnChanged = false
        editingAfter = false
        maxRawLength = maskToRaw[previousValidPosition(mask.length - 1)] + 1
        lastValidMaskPosition = findLastValidMaskPosition()
        initialized = true
        super.setOnFocusChangeListener { v, hasFocus ->
            if (this::focusChangeListener.isInitialized) focusChangeListener.onFocusChange(v, hasFocus)
            if (hasFocus()) {
                selectionChanged = false
                this@MaskedEditText.setSelection(lastValidPosition())
            }
        }
    }

    /**
     * Generates positions for values characters. For instance:
     * Input data: mask = "+7(###)###-##-##
     * After method execution:
     * rawToMask = [3, 4, 5, 6, 8, 9, 11, 12, 14, 15]
     * maskToRaw = [-1, -1, -1, 0, 1, 2, -1, 3, 4, 5, -1, 6, 7, -1, 8, 9]
     * charsInMask = "+7()- " (and space, yes)
     */
    private fun generatePositionArrays() {
        val aux = IntArray(mask.length)
        maskToRaw = IntArray(mask.length)
        var charsInMaskAux = ""
        var charIndex = 0
        for (index in mask.indices) {
            val currentChar = mask[index]
            if (currentChar == charRepresentation) {
                aux[charIndex] = index
                maskToRaw[index] = charIndex++
            } else {
                val charAsString = "$currentChar"
                if (!charsInMaskAux.contains(charAsString)) charsInMaskAux += charAsString
                maskToRaw[index] = -1
            }
        }
        if (charsInMaskAux.indexOf(CONSTANTS.SPACE) < 0) charsInMaskAux += CONSTANTS.SPACE
        //looks like this line is unused
        val charsInMask = charsInMaskAux.toCharArray()
        rawToMask = IntArray(charIndex)
        System.arraycopy(aux, 0, rawToMask, 0, charIndex)
    }

    //ambiguous method
    private fun makeMaskedTextWithHint(): CharSequence {
        val ssb = SpannableStringBuilder()
        //what's this?
        var mtrv: Int
        val maskFirstChunkEnd = rawToMask[0]
        for (i in mask.indices) {
            mtrv = maskToRaw[i]
            if (mtrv == -1) ssb.append(mask[i])
            else {
                if (mtrv < rawText.getLength()) ssb.append(rawText.charAt(mtrv))
                else {
                    val hintString = hint.toString()
                    ssb.append(hintString[maskToRaw[i]])
                }
            }
            if (keepHint && rawText.getLength() < rawToMask.size && i >= rawToMask[rawText.getLength()] || !keepHint && i >= maskFirstChunkEnd) {
                ssb.setSpan(ForegroundColorSpan(currentHintTextColor), i, i + 1, 0)
            }
        }
        return ssb
    }

    private fun makeMaskedText(): String {
        val maskedTextLength = if (rawText.getLength() < rawToMask.size) rawToMask[rawText.getLength()]
        else mask.length

        val maskedText = CharArray(maskedTextLength)
        for (i in maskedText.indices) {
            val rawIndex = maskToRaw[i]
            if (rawIndex == -1) maskedText[i] = mask[i]
            else maskedText[i] = rawText.charAt(rawIndex)
        }
        return String(maskedText)
    }

    private fun previousValidPosition(currentPosition: Int): Int {
        var newPosition = currentPosition
        while (newPosition >= 0 && maskToRaw[newPosition] == -1) {
            newPosition--
            if (newPosition < 0) return nextValidPosition(0)
        }
        return newPosition
    }

    private fun findLastValidMaskPosition(): Int {
        for (index in maskToRaw.indices.reversed()) if (maskToRaw[index] != -1) return index
        throw RuntimeException("Mask must contain at least one representation char")
    }

    private fun lastValidPosition(): Int {
        return if (rawText.getLength() == maxRawLength) rawToMask[rawText.getLength() - 1] + 1
        else nextValidPosition(rawToMask[rawText.getLength()])
    }

    private fun nextValidPosition(currentPosition: Int): Int {
        var nextPosition = currentPosition
        while (nextPosition < lastValidMaskPosition && maskToRaw[nextPosition] == -1) nextPosition++
        return if (nextPosition > lastValidMaskPosition) lastValidMaskPosition + 1
        else nextPosition
    }

    override fun setText(text: CharSequence, type: BufferType) {
        super.setText(text, type)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superParcelable = super.onSaveInstanceState()
        val state = Bundle()
        state.putParcelable("super", superParcelable)
        state.putString("text", getRawText())
        state.putBoolean("keepHint", isKeepHint())
        return state
    }

    private fun hasHint() = hint != null

    fun getRawText() = rawText.getText()

    fun isKeepHint() = keepHint

    fun setKeepHint(keepHint: Boolean) {
        this.keepHint = keepHint
        setText(getRawText())
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val bundle = state as Bundle
        keepHint = bundle.getBoolean("keepHint", false)
        super.onRestoreInstanceState(state.getParcelable("super"))
        val text = bundle.getString("text")
        setText(text)
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        var newCount = count
        if (!editingOnChanged && editingBefore) {
            editingOnChanged = true
            if (ignore) return
            if (newCount > 0) {
                val startingPosition = maskToRaw[nextValidPosition(start)]
                val addedString = s.subSequence(start, start + newCount).toString()
                newCount = rawText.addToString(filterText(addedString), startingPosition, maxRawLength)
                if (initialized) {
                    val currentPosition =
                        if (startingPosition + newCount < rawToMask.size) rawToMask[startingPosition + newCount]
                        else lastValidMaskPosition + 1
                    selectionIndex = nextValidPosition(currentPosition)
                }
            }
        }
    }

    /**
     * Filter text based on allowed and denied characters,
     * denied characters have priority over allowed characters
     *
     * @param text
     * @return
     */
    private fun filterText(text: String): String {
        var tempText = text
        for (deniedChar in deniedChars) tempText = tempText.replace("$deniedChar", "")
        val builder = StringBuilder(tempText.length)
        for (char in tempText) if (char in allowedChars) builder.append(char)
        return builder.toString()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        // On Android 4+ this method is being called more than 1 time if there is a hint in the EditText, what moves the cursor to left
        // Using the boolean var selectionChanged to limit to one execution
        var selStart = selStart
        var selEnd = selEnd
        if (initialized) {
            if (selectionChanged) {
                //check to see if the current selection is outside the already entered text
                if (selStart > rawText.getLength() - 1) {
                    val start = fixSelection(selStart)
                    val end = fixSelection(selEnd)
                    if (start >= 0 && end < text!!.length) setSelection(start, end)
                }
            } else {
                selStart = fixSelection(selStart)
                selEnd = fixSelection(selEnd)

                // exactly in this order. If getText.length() == 0 then selStart will be -1
                if (selStart > text!!.length) selStart = text!!.length
                if (selStart < 0) selStart = 0

                // exactly in this order. If getText.length() == 0 then selEnd will be -1
                if (selEnd > text!!.length) selEnd = text!!.length
                if (selEnd < 0) selEnd = 0
                setSelection(selStart, selEnd)
                selectionChanged = true
            }
        }
        super.onSelectionChanged(selStart, selEnd)
    }

    private fun fixSelection(selection: Int): Int {
        return if (selection > lastValidPosition()) lastValidPosition()
        else nextValidPosition(selection)
    }

    /**
     * @param listener - its onFocusChange() method will be called before performing MaskedEditText operations,
     * related to this event.
     */
    override fun setOnFocusChangeListener(listener: OnFocusChangeListener) {
        focusChangeListener = listener
    }

    fun setShouldKeepText(shouldKeepText: Boolean) {
        isKeepingText = shouldKeepText
    }

    fun getMask() = mask

    fun setMask(mask: String) {
        this.mask = mask
        cleanUp()
    }

    fun setImeActionEnabled(isEnabled: Boolean) {
        if (isEnabled) setOnEditorActionListener(onEditorActionListener)
        else setOnEditorActionListener(null)
    }

    fun getCharRepresentation() = charRepresentation

    fun setCharRepresentation(charRepresentation: Char) {
        this.charRepresentation = charRepresentation
        cleanUp()
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        if (!editingBefore) {
            editingBefore = true
            if (start > lastValidMaskPosition) ignore = true
            var rangeStart = start
            if (after == 0) rangeStart = erasingStart(start)
            val range = calculateRange(rangeStart, start + count)
            if (range.start != -1) rawText.subtractFromString(range)
            if (count > 0) selectionIndex = previousValidPosition(start)
        }
    }

    override fun afterTextChanged(editableString: Editable) {
        if (!editingAfter && editingBefore && editingOnChanged) {
            editingAfter = true
            if (hasHint() && (keepHint || rawText.getLength() == 0)) setText(makeMaskedTextWithHint())
            else setText(makeMaskedText())
            selectionChanged = false
            setSelection(selectionIndex)
            editingBefore = false
            editingOnChanged = false
            editingAfter = false
            ignore = false
        }
    }

    private fun erasingStart(start: Int): Int {
        var newStart = start
        while (newStart > 0 && maskToRaw[newStart] == -1) newStart--
        return newStart
    }

    private fun calculateRange(start: Int, end: Int): Range {
        val range = Range()
        var i = start
        while (i <= end && i < mask.length) {
            if (maskToRaw[i] != -1) {
                if (range.start == -1) range.start = maskToRaw[i]
                range.end = maskToRaw[i]
            }
            i++
        }
        if (end == mask.length) range.end = rawText.getLength()
        if (range.start == range.end && start < end) {
            val newStart = previousValidPosition(range.start - 1)
            if (newStart < range.start) range.start = newStart
        }
        return range
    }
}