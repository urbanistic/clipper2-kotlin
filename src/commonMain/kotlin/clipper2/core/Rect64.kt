package clipper2.core

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.max
import kotlin.math.min

@JsExport
public class Rect64(var left: Long, var top: Long, var right: Long, var bottom: Long) {

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
            left = Long.MAX_VALUE
            top = Long.MAX_VALUE
            right = Long.MIN_VALUE
            bottom = Long.MIN_VALUE
        }
    }

    @JsName("ofRect64")
    constructor(rec: Rect64) : this(rec.left, rec.top, rec.right, rec.bottom)

    var width: Long
        get() = right - left
        set(value) {
            right = left + value
        }
    var height: Long
        get() = bottom - top
        set(value) {
            bottom = top + value
        }

    fun asPath(): Path64 {
        return Path64.of(
            Point64(left, top),
            Point64(right, top),
            Point64(right, bottom),
            Point64(left, bottom)
        )
    }

    fun isEmpty(): Boolean {
        return bottom <= top || right <= left
    }

    fun midPoint(): Point64 {
        return Point64((left + right) / 2, (top + bottom) / 2)
    }

    @JsName("containsPoint")
    fun contains(pt: Point64): Boolean {
        return pt.x > left && pt.x < right && pt.y > top && pt.y < bottom
    }

    @JsName("containsRect")
    fun contains(rec: Rect64): Boolean {
        return rec.left >= left && rec.right <= right && rec.top >= top && rec.bottom <= bottom
    }

    fun intersects(rec: Rect64): Boolean {
        return max(left, rec.left) <= min(right, rec.right) &&
                max(top, rec.top) <= min(bottom, rec.bottom)
    }

    fun clone(): Rect64 {
        val varCopy = Rect64()
        varCopy.left = left
        varCopy.top = top
        varCopy.right = right
        varCopy.bottom = bottom
        return varCopy
    }

    companion object {
        private const val InvalidRect = "Invalid Rect64 assignment"
    }
}