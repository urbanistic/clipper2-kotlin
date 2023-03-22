package clipper2.engine

import Clipper
import clipper2.core.Path64
import kotlin.jvm.JvmOverloads


/**
 * PolyPath64 objects are contained inside PolyTree64s and represents a single
 * polygon contour. PolyPath64s can also contain children, and there's no limit
 * to nesting. Each child's Polygon will be inside its parent's Polygon.
 */
open class PolyPath64 @JvmOverloads constructor(parent: PolyPathBase? = null) : PolyPathBase(parent) {
    public var polygon: Path64? = null
        private set

    override fun addChild(p: Path64): PolyPathBase {
        val newChild = PolyPath64(this)
        newChild.setPolygon(p)
        children.add(newChild)
        return newChild
    }

    operator fun get(index: Int): PolyPath64 {
        if (index < 0 || index >= children.size) {
            throw IllegalStateException()
        }
        return children[index] as PolyPath64
    }

    fun area(): Double {
        var result: Double = if (polygon == null) {
            0.0
        } else {
            Clipper.area(polygon!!)
        }

        for (polyPathBase in children) {
            val child = polyPathBase as PolyPath64
            result += child.area()
        }

        return result
    }

    private fun setPolygon(value: Path64) {
        polygon = value
    }

    override fun toString(): String {
        return "children: $count, polygon: $polygon"
    }
}