package clipper2.core

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * PathsD represent one or more PathD structures. While a single path can
 * represent a simple polygon, multiple paths are usually required to define
 * complex polygons that contain one or more holes.
 */
@JsExport
public class PathsD : MutableList<PathD> by mutableListOf() {

    public override fun toString(): String {
        val bld: StringBuilder = StringBuilder()
        for (path in this) {
            bld.append(path.toString() + "\n")
        }
        return bld.toString()
    }

    companion object {
        @JsName("ofPathsD")
        public fun of(elements: PathsD): PathsD {
            val paths = PathsD()
            paths.addAll(elements)
            return paths
        }

        @JsName("ofPathDs")
        public fun of(vararg elements: PathD): PathsD {
            val paths = PathsD()
            paths.addAll(elements)
            return paths
        }

        public fun ofPaths64(paths: Paths64): PathsD {
            val result = PathsD() // path.size
            for (path in paths) {
                result.add(PathD.ofPath64(path))
            }
            return result
        }
    }
}
