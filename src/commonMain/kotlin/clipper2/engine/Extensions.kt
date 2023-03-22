package clipper2.engine

fun MutableList<Long>.addIfMissingAndSort(element: Long) {
    if(!this.contains(element)){
        this.add(element)
        this.sort()
    }
}

fun <T : Any> MutableList<T>.pollLast(): T {
    val result = this.last()
    this.remove(result)
    return result
}

fun <T : Any> MutableList<T>.swap(i: Int, j: Int) {
    if(i == j) return

    val oi = this[i]
    this[i] = this[j]
    this[j] = oi
}

