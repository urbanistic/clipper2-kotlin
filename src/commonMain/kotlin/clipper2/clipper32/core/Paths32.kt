@file:Suppress("unused")

package clipper2.clipper32.core

import clipper2.core.PathsD
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Paths32 represent one or more Path32 structures. While a single path can
 * represent a simple polygon, multiple paths are usually required to define
 * complex polygons that contain one or more holes.
 */
@JsExport
class Paths32 : MutableList<Path32> by mutableListOf() {

    override fun toString(): String {
        val bld = StringBuilder()
        for (path in this) {
            bld.append(path.toString() + "\n")
        }
        return bld.toString()
    }

    fun asArray(): Array<Path32> {
        return this.toTypedArray()
    }

    companion object {
        fun copy(elements: Paths32): Paths32 {
            val paths = Paths32()
            paths.addAll(elements)
            return paths
        }

        @JsName("ofPath32s")
        fun of(vararg elements: Path32): Paths32 {
            val paths = Paths32()
            paths.addAll(elements)
            return paths
        }

        fun ofPathsD(paths: PathsD): Paths32 {
            val result = Paths32() // path.size
            for (path in paths) {
                result.add(Path32.ofPathD(path))
            }
            return result
        }
    }
}
