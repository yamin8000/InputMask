package com.yamin.inputmask

class RawText {

    private var text = ""

    /**
     * text = 012345678, range = 123 =&gt; text = 0456789
     * @param range given range
     */
    fun subtractFromString(range: Range) {
        var firstPart = ""
        var lastPart = ""
        if (range.start in 1..text.length) firstPart = text.substring(0 until range.start)
        if (range.end in text.indices) lastPart = text.substring(range.end until text.length)
        text = firstPart + lastPart
    }

    /**
     * @param newString New String to be added
     * @param start     Position to insert newString
     * @param maxLength Maximum raw text length
     * @return Number of added characters
     */
    fun addToString(newString: String, start: Int, maxLength: Int): Int {
        var tempString = newString
        var firstPart = ""
        var lastPart = ""

        when {
            tempString.isEmpty() -> return 0
            start < 0 -> throw IllegalStateException("Start position must be non-negative")
            start > text.length -> {
                throw IllegalStateException("Start position must be less than the actual text length")
            }
        }

        var count = tempString.length
        if (start > 0) firstPart = text.substring(0 until start)
        if (start in text.indices) lastPart = text.substring(start until text.length)
        if (getLength() + tempString.length > maxLength) {
            count = maxLength - getLength()
            tempString = tempString.substring(0 until count)
        }
        text = firstPart + tempString + lastPart
        return count
    }

    fun getLength() = text.length
    fun charAt(position: Int) = text[position]
    fun getText() = text
}