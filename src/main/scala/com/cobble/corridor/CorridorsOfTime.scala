package com.cobble.corridor

import java.awt.Dimension
import java.awt.event.{WindowAdapter, WindowEvent}
import java.net.URL
import java.nio.file.{Files, Paths}

import com.cobble.corridor.CodeSymbol.CodeSymbol
import javax.swing.{JFrame, SwingUtilities}
import org.graphstream.graph.implementations.MultiGraph
import org.graphstream.ui.swingViewer.{View, Viewer}
import org.graphstream.ui.swingViewer.Viewer.CloseFramePolicy
import org.graphstream.ui.swingViewer.util.DefaultShortcutManager
import play.api.libs.json._

import scala.io.{BufferedSource, Source}


object CorridorsOfTime {

    final val TITLE: String = "The Fuck Bungie"

    var codeMap: CodeMap = _

    val graph: MultiGraph = new MultiGraph(TITLE)

    def main(args: Array[String]): Unit = {
        //        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer")
        generateData()
        generateGraph()
        setupStyle()
        SwingUtilities.invokeLater(() => createJframe())
    }

    def generateData(): Unit = {
        if (!(Files.exists(Paths.get(".", "codes.json")) || Files.exists(Paths.get("..", "codes.json")))) { //Clean this up maybe
            System.err.println("No ./codes.json or ../codes.json file found")
            System.exit(2)
        }

        val codesSource: BufferedSource = if (Files.exists(Paths.get(".", "codes.json")))
            Source.fromFile("./codes.json")
        else
            Source.fromFile("../codes.json")

        val codeJson: JsValue = Json.parse(codesSource.getLines.mkString("\n"))
        codesSource.close()
        implicit val codeSymbolFormat: Format[CodeSymbol] = new Format[CodeSymbol] {
            def reads(json: JsValue): JsResult[CodeSymbol] = {
                val str: String = json.as[String].trim.toUpperCase
                if (CodeSymbol.values.exists(_.toString == str))
                    JsSuccess(CodeSymbol.withName(str))
                else
                    JsSuccess(CodeSymbol.UNKNOWN)
            }

            def writes(codeSymbol: CodeSymbol): JsValue = JsString(codeSymbol.toString)
        }
        implicit val codeFormat: Format[Code] = Json.format[Code]
        val codes: Array[Code] = codeJson("codes").as[Array[Code]].filter(_.isValid).distinct
        codeMap = new CodeMap(codes)
    }

    def generateGraph(): Unit = {
        codeMap.generateMap()
        val generator: HexGenerator = new HexGenerator(codeMap)
        generator.addSink(graph)
        generator.begin()
        var added: Boolean = false
        do {
            added = generator.nextEvents()
        } while (added)
        generator.end()
    }

    def setupStyle(): Unit = {
        var cssPath: String = ""
        if (Files.exists(Paths.get(".", "Graph.css")))
            cssPath = Paths.get(".", "Graph.css").toString
        else if (Files.exists(Paths.get("..", "Graph.css")))
            cssPath = Paths.get("..", "Graph.css").toString
        if (cssPath.isEmpty)
            System.out.println("No ./Graph.css or ../Graph.css file found. Not adding any styling.")
        else
            graph.addAttribute("ui.stylesheet", s"url('$cssPath')")
//        val cssUrl: URL = getClass.getClassLoader.getResource("Graph.css")

        graph.addAttribute("ui.quality")
        graph.addAttribute("ui.antialias")
    }

    def createJframe(): Unit = {
        Thread.setDefaultUncaughtExceptionHandler((t: Thread, e: Throwable) => {
            println(s"Unhandled Exception due to $t throwing $e")
            println("************START STACKTRACE************")
            e.printStackTrace()
            println("************END STACKTRACE************")
            t.getThreadGroup.list()
        })
        val viewer: Viewer = new Viewer(graph, Viewer.ThreadingModel.GRAPH_IN_SWING_THREAD)
        viewer.setCloseFramePolicy(CloseFramePolicy.EXIT)
        val view: View = viewer.addDefaultView(false)
        view.setShortcutManager(new CorridorShortcutManager(graph))
        val frame: JFrame = new JFrame(TITLE)
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
        frame.addWindowListener(new WindowAdapter {
            override def windowClosing(e: WindowEvent): Unit = {
                viewer.close()
            }
        })
        frame.add(view)
        frame.setPreferredSize(new Dimension(800, 600))
        frame.pack()
        frame.setVisible(true)
        frame.requestFocus()
    }

}
