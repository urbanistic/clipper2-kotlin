package clipper2.clipper32.core

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.max
import kotlin.math.min

@JsExport
public class Rect32(var left: Int, var top: Int, var right: Int, var bottom: Int) {

    init{
        if (right < left || bottom < top) {
            throw IllegalArgumentException(InvalidRect)
        }
    }

    @JsName("zero")
    constructor() : this(0, 0, 0, 0)

    @JsName("ofValid")
    constructor(isValid: Boolean) : this() {
        if (isValid) {
            left = 0
            top = 0
            right = 0
            bottom = 0
        } else {
            left = Int.MAX_VALUE
            top = Int.MAX_VALUE
            right = Int.MIN_VALUE
            bottom = Int.MIN_VALUE
        }
    }

    @JsName("ofRect32")
    constructor(rec: Rect32) : this(rec.left, rec.top, rec.right, rec.bottom)

    var width: Int
        get() = right - left
        set(value) {
            right = left + value
        }
    var height: Int
        get() = bottom - top
        set(value) {
            bottom = top + value
        }

    fun asPath(): Path32 {
        return Path32.of(
                Point32(left, top),
                Point32(right, top),
                Point32(right, bottom),
                Point32(left, bottom)
        )
    }

    fun isEmpty(): Boolean {
        return bottom <= top || right <= left
    }

    fun midPoint(): Point32 {
        return Point32((left + right) / 2, (top + bottom) / 2)
    }

    @JsName("containsPoint")
    fun contains(pt: Point32): Boolean {
        return pt.x > left && pt.x < right && pt.y > top && pt.y < bottom
    }

    @JsName("containsRect")
    fun contains(rec: Rect32): Boolean {
        return rec.left >= left && rec.right <= right && rec.top >= top && rec.bottom <= bottom
    }

    fun intersects(rec: Rect32): Boolean {
        return max(left, rec.left) <= min(right, rec.right) &&
                max(top, rec.top) <= min(bottom, rec.bottom)
    }

    fun clone(): Rect32 {
        val varCopy = Rect32()
        varCopy.left = left
        varCopy.top = top
        varCopy.right = right
        varCopy.bottom = bottom
        return varCopy
    }

    companion object {
        private const val InvalidRect = "Invalid Rect32 assignment"
    }
}