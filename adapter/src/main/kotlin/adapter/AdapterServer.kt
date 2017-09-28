package adapter

import adapter.AdapterServer.handleExceptions
import adapter.AdapterServer.parameterMap
import adapter.AdapterServer.path
import adapter.AdapterServer.pathSingleSlash
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.http.javadsl.model.ContentTypes
import akka.http.javadsl.model.HttpEntities
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.ExceptionHandler
import akka.http.javadsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

object AdapterServer : AllDirectives() {

  private val logger = LoggerFactory.getLogger(javaClass)
  private val mapper = ObjectMapper()
    .registerModule(KotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private val invalidDateHandler = ExceptionHandler.newBuilder()
    .match(DateTimeParseException::class.java, { ex ->
      complete(StatusCodes.BAD_REQUEST, "'${ex.parsedString}' is invalid date.")
    })
    .build()

  @JvmStatic
  fun main(args: Array<String>) {
    val system = ActorSystem.create("adapter")
    val materializer = ActorMaterializer.create(system)

    val githubArchiveService = GithubArchiveClient(system, materializer, mapper)
    val flow = createRoute(githubArchiveService).flow(system, materializer)

    Http.get(system).bindAndHandle(flow, ConnectHttp.toHost("localhost", 8080), materializer)

    logger.info("Press RETURN to exit")
    System.`in`.read()
    system.terminate()
  }

  private fun createRoute(githubArchiveClient: GithubArchiveClient): Route {
    return route(
      pathSingleSlash {
        complete(StatusCodes.OK)
      },
      handleExceptions(invalidDateHandler, {
        parameterMap { params ->
          val now = LocalDateTime.now()
          val date = params["date"]?.let { LocalDate.parse(it) } ?: now.toLocalDate()
          val from = params["from"]?.toInt() ?: now.hour
          val to = params["to"]?.toInt() ?: now.hour + 1
          route(
            path("forks", {
              val forks = githubArchiveClient.forks(date, from..to)
                .map { event -> event.forkee() }
              complete(toResponse(forks))
            })
          )
        }
      })
    )
  }

  private fun toResponse(source: Source<out Any, NotUsed>): HttpResponse {
    val stream = source.map { ByteString.fromArray(mapper.writeValueAsBytes(it)).concat(ByteString.fromString("\n")) }
    return HttpResponse.create().withEntity(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, stream))
  }
}
