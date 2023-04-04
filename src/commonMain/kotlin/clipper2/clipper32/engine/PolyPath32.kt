package clipper2.clipper32.engine

import Clipper
import clipper2.clipper32.core.Path32
import kotlin.jvm.JvmOverloads

/**
 * PolyPath32 objects are contained inside PolyTree32s and represents a single
 * polygon contour. PolyPath32s can also contain children, and there's no limit
 * to nesting. Each child's Polygon will be inside its parent's Polygon.
 */
open class PolyPath32 @JvmOverloads constructor(parent: PolyPathBase32? = null) : PolyPathBase32(parent) {
    public var polygon: Path32? = null
        private set

    override fun addChild(p: Path32): PolyPathBase32 {
        val newChild = PolyPath32(this)
        newChild.setPolygon(p)
        children.add(newChild)
        return newChild
    }

    operator fun get(index: Int): PolyPath32 {
        if (index < 0 || index >= children.size) {
            throw IllegalStateException()
        }
        return children[index] as PolyPath32
    }

    fun area(): Double {
        var result: Double = if (polygon == null) {
            0.0
        } else {
            Clipper.area(polygon!!)
        }

        for (polyPathBase in children) {
            val child = polyPathBase as PolyPath32
            result += child.area()
        }

        return result
    }

    private fun setPolygon(value: Path32) {
        polygon = value
    }

    override fun toString(): String {
        return "children: $count, polygon: $polygon"
    }
}
