package clipper2.engine

import clipper2.core.Path32
import clipper2.core.Path64
import kotlin.js.JsExport

@JsExport
abstract class PolyPathBase32(var parent: PolyPathBase32? = null) : Iterable<PolyPathBase32?> {
    var children: MutableList<PolyPathBase32> = mutableListOf()

    fun childrenAsArray(): Array<PolyPathBase32>{
        return children.toTypedArray()
    }

    override fun iterator(): NodeIterator32 {
        return NodeIterator32(children)
    }

    private fun getLevel(): Int {
        var result = 0
        var pp: PolyPathBase32? = parent
        while (pp != null) {
            ++result
            pp = pp.parent
        }
        return result
    }

    val isHole: Boolean
        /**
         * Indicates whether the Polygon property represents a hole or the outer bounds
         * of a polygon.
         */
        get() {
            val lvl = getLevel()
            return lvl != 0 && lvl and 1 == 0
        }

    val count: Int
        /**
         * Indicates the number of contained children.
         */
        get() = children.size

    abstract fun addChild(p: Path32): PolyPathBase32?

    /**
     * This method clears the Polygon and deletes any contained children.
     */
    fun clear() {
        children.clear()
    }
}