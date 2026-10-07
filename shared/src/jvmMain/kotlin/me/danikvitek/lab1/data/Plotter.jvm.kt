package me.danikvitek.lab1.data

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.export.toPNG
import org.jetbrains.kotlinx.kandy.letsplot.export.toSVG
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.x
import kotlin.io.path.Path

class JvmPlotter() : Plotter() {
    override fun plot(citiesAmount: Int): PlotProvider {
        val lengths = buildList {
            addAll(buffer)
        }
        val df = dataFrameOf(
            "Length" to lengths,
            "Iteration" to lengths.indices.map { it + 1 },
        )

        val plot = df.plot {
            line {
                x(Iteration)
                y(Length)
            }
            x {
                continuous(min = 1)
            }
            layout {
                title = "$citiesAmount cities"
                xAxisLabel = "Iteration"
                yAxisLabel = "Length"

                size = 1000 to 450
            }
        }

        return KandyPlotProvider(plot, citiesAmount)
    }
}

val userWorkingDirectory
    get() = System.getProperty("user.dir")!!
val userHomeDirectory
    get() = System.getProperty("user.home")!!

class KandyPlotProvider(private val plot: Plot, private val citiesAmount: Int) : PlotProvider {
    override val svg: String by lazy { plot.toSVG() }
    override val png: ByteArray by lazy { plot.toPNG() }

    override fun save() {
        val path = plot.save(
            filename = "$citiesAmount-cities.svg",
            path = Path(userWorkingDirectory, "plots").toString(),
        )
        println("The plot was saved to $path")
    }
}

actual fun getPlotter(): Plotter = JvmPlotter()