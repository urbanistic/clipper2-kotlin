package clipper2.engine

import clipper2.core.PathType
import clipper2.core.Paths64

class ReuseableDataContainer64 {
    val minimaList: MutableList<LocalMinima>
    val vertexList: MutableList<ClipperBase.Vertex?>

    init{
        minimaList = mutableListOf()
        vertexList = mutableListOf()
    }

    fun clear()
    {
        minimaList.clear()
        vertexList.clear()
    }

    fun addPaths(paths: Paths64, pt: PathType, isOpen: Boolean)
    {
        ClipperBase.ClipperEngine.addPathsToVertexList(paths, pt, isOpen, minimaList, vertexList)
    }
}