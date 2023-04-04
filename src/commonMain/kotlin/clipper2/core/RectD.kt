@file:Suppress("unused")

package clipper2.core

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.max
import kotlin.math.min

@JsExport
class RectD(var left: Double, var top: Double, var right: Double, var bottom: Double) {

    init {
        if (right < left || bottom < top) {
            throw IllegalArgumentException(InvalidRect)
        }
    }

    @JsName("zero")
    constructor() : this(0.0, 0.0, 0.0, 0.0)

    @JsName("ofRectD")
    constructor(rec: RectD) : this(rec.left, rec.top, rec.right, rec.bottom)

    @JsName("ofValid")
    constructor(isValid: Boolean) : this() {
        if (isValid) {
            left = 0.0
            top = 0.0
            right = 0.0
            bottom = 0.0
        } else {
            left = Double.MAX_VALUE
            top = Double.MAX_VALUE
            right = -Double.MAX_VALUE
            bottom = -Double.MAX_VALUE
        }
    }

    var width: Double
        get() = right - left
        set(value) {
            right = left + value
        }
    var height: Double
        get() = bottom - top
        set(value) {
            bottom = top + value
        }

    fun isEmpty(): Boolean {
        return bottom <= top || right <= left
    }

    fun midPoint(): PointD {
        return PointD((left + right) / 2, (top + bottom) / 2)
    }

    @JsName("containsPoint")
    fun contains(pt: PointD): Boolean {
        return pt.x > left && pt.x < right && pt.y > top && pt.y < bottom
    }

    @JsName("containsRect")
    fun contains(rec: RectD): Boolean {
        return rec.left >= left && rec.right <= right && rec.top >= top && rec.bottom <= bottom
    }

    fun intersects(rec: RectD): Boolean {
        return max(left, rec.left) < min(right, rec.right) &&
            max(top, rec.top) < min(bottom, rec.bottom)
    }

    fun asPath(): PathD {
        return PathD.of(
            PointD(left, top),
            PointD(right, top),
            PointD(right, bottom),
            PointD(left, bottom)
        )
    }

    fun clone(): RectD {
        val varCopy = RectD()
        varCopy.left = left
        varCopy.top = top
        varCopy.right = right
        varCopy.bottom = bottom
        return varCopy
    }

    companion object {
        private const val InvalidRect = "Invalid RectD assignment"
    }
}
