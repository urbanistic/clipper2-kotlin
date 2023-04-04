@file:Suppress("unused")

package clipper2.engine

import clipper2.core.PathType

class LocalMinima {
    var vertex: ClipperBase.Vertex? = null
    var polytype: PathType? = PathType.Subject
    var isOpen = false

    constructor()
    constructor(vertex: ClipperBase.Vertex?, polytype: PathType?) : this(vertex, polytype, false)
    constructor(vertex: ClipperBase.Vertex?, polytype: PathType?, isOpen: Boolean) {
        this.vertex = vertex
        this.polytype = polytype
        this.isOpen = isOpen
    }

    fun opEquals(o: LocalMinima): Boolean {
        return vertex === o.vertex
    }

    fun opNotEquals(o: LocalMinima): Boolean {
        return vertex !== o.vertex
    }

    override fun equals(other: Any?): Boolean {
        if (other is LocalMinima) {
            return this === other
        }
        return false
    }

    override fun hashCode(): Int {
        return vertex.hashCode()
    }

    protected fun clone(): LocalMinima {
        val varCopy = LocalMinima()
        varCopy.vertex = vertex
        varCopy.polytype = polytype
        varCopy.isOpen = isOpen
        return varCopy
    }
}
