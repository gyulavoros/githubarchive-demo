package analyser

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.Query
import akka.http.javadsl.model.Uri
import akka.japi.Pair
import akka.stream.Materializer
import akka.stream.javadsl.Framing
import akka.stream.javadsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import scala.util.Failure
import scala.util.Success
import shared.Forkee
import java.util.*

class AdapterClient(system: ActorSystem, materializer: Materializer, private val mapper: ObjectMapper) {

  companion object {
    private val NewLine = ByteString.fromString("\n")
  }

  private val flow = Http.get(system).superPool<String>(materializer)

  fun forks(): Source<Forkee, NotUsed> {
    val query = Query.create(
      Pair.create("date", "2017-09-08"),
      Pair.create("from", "15"),
      Pair.create("to", "16")
    )
    val uri = Uri.create("http://localhost:8080/forks").query(query)
    val request = Pair.create(HttpRequest.create().withUri(uri), UUID.randomUUID().toString())
    return Source.single(request)
      .via(flow)
      .map { it.first() }
      .flatMapConcat { result ->
        when (result) {
          is Success -> Source.single(result.value())
          else -> Source.failed((result as Failure).exception())
        }
      }
      .flatMapConcat { it.entity().withoutSizeLimit().dataBytes }
      .via(Framing.delimiter(NewLine, Int.MAX_VALUE))
      .map { line -> mapper.readValue<Forkee>(line.toArray()) }
  }
}
