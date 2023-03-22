package clipper2.engine

import clipper2.core.Path64
import kotlin.js.JsExport

@JsExport
abstract class PolyPathBase(var parent: PolyPathBase? = null) : Iterable<PolyPathBase?> {
    var children: MutableList<PolyPathBase> = mutableListOf()

    fun childrenAsArray(): Array<PolyPathBase>{
        return children.toTypedArray()
    }

    override fun iterator(): NodeIterator {
        return NodeIterator(children)
    }

    private fun getLevel(): Int {
        var result = 0
        var pp: PolyPathBase? = parent
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

    abstract fun addChild(p: Path64): PolyPathBase?

    /**
     * This method clears the Polygon and deletes any contained children.
     */
    fun clear() {
        children.clear()
    }
}