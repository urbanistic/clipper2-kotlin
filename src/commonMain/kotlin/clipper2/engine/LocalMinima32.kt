package clipper2.engine

import clipper2.core.PathType


class LocalMinima32 {
    var vertex: ClipperBase32.Vertex? = null
    var polytype: PathType? = PathType.Subject
    var isOpen = false

    constructor()
    constructor(vertex: ClipperBase32.Vertex?, polytype: PathType?) : this(vertex, polytype, false)
    constructor(vertex: ClipperBase32.Vertex?, polytype: PathType?, isOpen: Boolean) {
        this.vertex = vertex
        this.polytype = polytype
        this.isOpen = isOpen
    }

    fun opEquals(o: LocalMinima32): Boolean {
        return vertex === o.vertex
    }

    fun opNotEquals(o: LocalMinima32): Boolean {
        return vertex !== o.vertex
    }

    override fun equals(other: Any?): Boolean {
        if (other is LocalMinima32) {
            return this === other
        }
        return false
    }

    override fun hashCode(): Int {
        return vertex.hashCode()
    }

    protected fun clone(): LocalMinima32 {
        val varCopy = LocalMinima32()
        varCopy.vertex = vertex
        varCopy.polytype = polytype
        varCopy.isOpen = isOpen
        return varCopy
    }
}