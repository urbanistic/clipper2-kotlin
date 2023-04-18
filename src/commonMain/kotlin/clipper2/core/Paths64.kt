@file:Suppress("unused")

package clipper2.core

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Paths64 represent one or more Path64 structures. While a single path can
 * represent a simple polygon, multiple paths are usually required to define
 * complex polygons that contain one or more holes.
 */
@JsExport
class Paths64 : MutableList<Path64> by mutableListOf() {

    override fun toString(): String {
        val bld = StringBuilder()
        for (path in this) {
            bld.append(path.toString() + "\n")
        }
        return bld.toString()
    }

    fun asArray(): Array<Path64> {
        return this.toTypedArray()
    }

    companion object {
        fun copy(elements: Paths64): Paths64 {
            val paths = Paths64()
            paths.addAll(elements)
            return paths
        }

        @JsName("ofPath64s")
        fun of(vararg elements: Path64): Paths64 {
            val paths = Paths64()
            paths.addAll(elements)
            return paths
        }

        fun ofPathsD(paths: PathsD): Paths64 {
            val result = Paths64() // path.size
            for (path in paths) {
                result.add(Path64.ofPathD(path))
            }
            return result
        }
    }
}
