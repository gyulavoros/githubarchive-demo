package analyser

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import analyser.AnalyserServer.path
import analyser.AnalyserServer.pathSingleSlash
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory

object AnalyserServer : AllDirectives() {

  private val logger = LoggerFactory.getLogger(javaClass)
  private val mapper = ObjectMapper()
    .registerModule(KotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @JvmStatic
  fun main(args: Array<String>) {
    val system = ActorSystem.create("analyser")
    val materializer = ActorMaterializer.create(system)

    val adapterClient = AdapterClient(system, materializer, mapper)
    val flow = createRoute(adapterClient).flow(system, materializer)

    Http.get(system).bindAndHandle(flow, ConnectHttp.toHost("localhost", 8081), materializer)

    logger.info("Press RETURN to exit")
    System.`in`.read()
    system.terminate()
  }

  private fun createRoute(adapterClient: AdapterClient): Route {
    return route(
      pathSingleSlash {
        getFromResource("web/index.html")
      },
      path("fork-counts", {
        val flow = Flow.fromSinkAndSource(Sink.ignore<Message>(), forkCounts(adapterClient))
        handleWebSocketMessages(flow)
      })
    )
  }

  private fun forkCounts(adapterClient: AdapterClient): Source<Message, NotUsed> {
    return adapterClient.forks()
      .scan(mutableMapOf<String, Int>(), { stats, fork ->
        val count = stats[fork.forkedUrl] ?: 0
        stats[fork.forkedUrl] = count + 1
        stats
      })
      .map { stats -> stats.toList().sortedByDescending { (_, v) -> v }.toMap() }
      .map { stats -> TextMessage.create(mapper.writeValueAsString(stats.map { ForkRecord(it.key, it.value) })) }
  }
}

private data class ForkRecord(val name: String, val count: Int)
