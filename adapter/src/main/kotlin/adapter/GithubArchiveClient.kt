package adapter

import adapter.model.Event
import adapter.model.EventType
import adapter.model.ForkEvent
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.coding.Coder
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.headers.ContentEncoding
import akka.http.javadsl.model.headers.HttpEncodings
import akka.japi.Pair
import akka.stream.Materializer
import akka.stream.javadsl.Framing
import akka.stream.javadsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import scala.util.Failure
import scala.util.Success
import java.time.LocalDate

class GithubArchiveClient(system: ActorSystem, materializer: Materializer, private val mapper: ObjectMapper) {

  companion object {
    private val NewLine = ByteString.fromString("\n")
  }

  private val flow = Http.get(system).cachedHostConnectionPool<String>("data.githubarchive.org", materializer)

  fun forks(date: LocalDate, timeRange: IntRange): Source<ForkEvent, NotUsed> {
    return fetch(EventType.ForkEvent.name, date, timeRange)
  }

  private inline fun <reified T : Event> fetch(type: String, date: LocalDate, timeRange: IntRange): Source<T, NotUsed> {
    val requests = Source.from(timeRange)
      .map { time ->
        val path = "/$date-$time.json.gz"
        Pair.create(HttpRequest.create(path), path)
      }
    return requests
      .via(flow)
      .map { it.first() }
      .flatMapConcat { result ->
        when (result) {
          is Success -> Source.single(result.value())
          else -> Source.failed((result as Failure).exception())
        }
      }
      .map { it.addHeader(ContentEncoding.create(HttpEncodings.GZIP)) }
      .map { Coder.Gzip.decodeMessage(it) }
      .flatMapConcat { it.entity().withoutSizeLimit().dataBytes }
      .via(Framing.delimiter(NewLine, Int.MAX_VALUE))
      .flatMapMerge(4, { line ->
        val tree = mapper.readTree(line.utf8String())
        when (tree.get("type").textValue()) {
          type -> Source.single(mapper.treeToValue<T>(tree))
          else -> Source.empty()
        }
      })
  }
}
