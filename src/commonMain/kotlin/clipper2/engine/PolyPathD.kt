package clipper2.engine

import Clipper.area
import Clipper.scalePathD
import clipper2.core.Path64
import clipper2.core.PathD
import kotlin.js.JsExport

@JsExport
open class PolyPathD internal constructor(parent: PolyPathBase? = null) : PolyPathBase(parent) {
    var polygon: PathD? = null
        private set

    var scale = 0.0

    override fun addChild(p: Path64): PolyPathBase? {
        val newChild = PolyPathD(this)
        newChild.scale = scale
        newChild.setPolygon(scalePathD(p, scale))
        children.add(newChild)
        return newChild
    }

    operator fun get(index: Int): PolyPathD {
        if (index < 0 || index >= children.size) {
            IllegalStateException()
        }
        return children[index] as PolyPathD
    }

    fun area(): Double {
        var result: Double = if (polygon == null) {
            0.0
        } else {
            area(polygon!!)
        }

        for (polyPathBase in children) {
            val child = polyPathBase as PolyPathD
            result += child.area()
        }
        return result
    }

    private fun setPolygon(value: PathD) {
        polygon = value
    }

    override fun toString(): String {
        return "children: $count, polygon: $polygon"
    }
}
