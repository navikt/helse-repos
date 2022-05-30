import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureRouting(Repositories(System.getenv("GITHUB_API_TOKEN")))
        install(ContentNegotiation) {
            json()
        }
    }.start(wait = true)
}


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