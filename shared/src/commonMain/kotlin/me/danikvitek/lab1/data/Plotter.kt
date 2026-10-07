package me.danikvitek.lab1.data

abstract class Plotter() {
    protected val buffer = ArrayList<Float>()

    fun recordIteration(value: Float) {
        buffer.add(value)
    }

    fun reset() = buffer.clear()

    abstract fun plot(citiesAmount: Int): PlotProvider
}

interface PlotProvider {
    val svg: String
    val png: ByteArray

    fun save()
}

expect fun getPlotter(): Plotter