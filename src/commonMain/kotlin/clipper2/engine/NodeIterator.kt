package clipper2.engine

class NodeIterator(var ppbList: List<PolyPathBase>) : MutableIterator<PolyPathBase> {

    var position = 0

    override fun hasNext(): Boolean {
        return position < ppbList.size
    }

    override fun next(): PolyPathBase {
        if (position < 0 || position >= ppbList.size) {
            NoSuchElementException()
        }
        return ppbList[position++]
    }

    override fun remove() {
        TODO("Not yet implemented")
    }
}