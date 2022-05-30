import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

val mapper = jacksonObjectMapper().registerModule(KotlinModule())

class Repositories(private val token: String) {
    private val client = HttpClient(CIO)
    internal val repos = lazy { refresh() }

    internal fun refresh(): List<Repo> {
        val response: String = runBlocking {
            client.get("https://api.github.com/orgs/navikt/teams/tbd/repos?per_page=100") {
                header(HttpHeaders.Authorization, "token $token")
            }.bodyAsText()
        }
        val json = mapper.readTree(response)
        val repos = json.map { Repo.fromJson(it) { name -> commits(name) } }.sortedBy { it.commits }
        return repos
    }

    // Henter commits basert på pagination i github APIet
    private fun commits(repo: String): Int {
        val response = runBlocking {
            client.get("https://api.github.com/repos/navikt/$repo/commits?per_page=1") {
                header(HttpHeaders.Authorization, "token $token")
            }.headers
        }
        val result = Regex("(&page=\\d*)").findAll(response["Link"].toString())
        return Integer.parseInt(result.toList()[1].value.split("=")[1])
    }
}

data class Repo(
    val name: String,
    val description: String,
    val htmlUrl: String,
    val topics: String,
    val private: String,
    val commits: Int
) {

    companion object {
        fun fromJson(jsonNode: JsonNode, commits: (String) -> Int): Repo {
            val name = jsonNode["name"].asText()
            return Repo(
                name,
                jsonNode["description"].asText(),
                jsonNode["html_url"].asText(),
                jsonNode["topics"].asText(),
                jsonNode["private"].asText(),
                commits(name)
            )
        }
    }
}

fun Iterable<Repo>.toJson() = mapper.writeValueAsString(this)