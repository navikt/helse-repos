import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes


fun main() = runBlocking {  start() }

suspend fun start() {
    val logger = LoggerFactory.getLogger("helse-repos")
    val repositories = Repositories(System.getenv("GITHUB_API_TOKEN"))
    val ktorServer = ktor(repositories)
    try {
        coroutineScope {
            launch {
                while (true) {
                    delay(10.minutes)
                    logger.info("Refreshing repositories list")
                    repositories.refresh()
                    logger.info("Refreshing repositories list complete")
                }
            }
        }

    } finally {
        val gracePeriod = 5000L
        val forcefulShutdownTimeout = 30000L
        logger.info("shutting down ktor, waiting $gracePeriod ms for workers to exit. Forcing shutdown after $forcefulShutdownTimeout ms")
        ktorServer.stop(gracePeriod, forcefulShutdownTimeout)
        logger.info("ktor shutdown complete: end of life. goodbye.")
    }
}



fun ktor(repositories: Repositories) = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
    configureRouting(repositories)
    install(ContentNegotiation) {
        json()
    }
}.start(wait = false)


fun Application.configureRouting(repositories: Repositories) {
    routing {
        get("/") {
            call.respondText("TBD repos: \n\t/repos")
        }
        get("repos") {
            call.respondText(repositories.repos.value.toJson(), ContentType.Application.Json)
        }
    }
}