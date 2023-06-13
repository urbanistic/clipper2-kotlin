package clipper2.clipper32.engine

import clipper2.clipper32.core.Paths32
import clipper2.core.PathType

class ReuseableDataContainer32 {
    val minimaList: MutableList<LocalMinima32>
    val vertexList: MutableList<ClipperBase32.Vertex?>

    init{
        minimaList = mutableListOf()
        vertexList = mutableListOf()
    }

    fun clear()
    {
        minimaList.clear()
        vertexList.clear()
    }

    fun addPaths(paths: Paths32, pt: PathType, isOpen: Boolean)
    {
        ClipperBase32.ClipperEngine32.addPathsToVertexList(paths, pt, isOpen, minimaList, vertexList)
    }
}