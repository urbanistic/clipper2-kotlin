package clipper2.clipper32.engine

class NodeIterator32(var ppbList: List<PolyPathBase32>) : MutableIterator<PolyPathBase32> {

    var position = 0

    override fun hasNext(): Boolean {
        return position < ppbList.size
    }

    override fun next(): PolyPathBase32 {
        if (position < 0 || position >= ppbList.size) {
            throw  NoSuchElementException()
        }
        return ppbList[position++]
    }

    override fun remove() {
        TODO("Not yet implemented")
    }
}
