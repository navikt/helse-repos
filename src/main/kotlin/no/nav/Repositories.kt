import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
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
    internal var repos = lazy { repos() }
    private val client = HttpClient(CIO)
    private val filters = listOf(
        { repo: JsonNode -> repo["archived"].asText() == "false"},
        { repo: JsonNode -> repo["disabled"].asText() == "false" }
    )

    internal fun refresh(): List<Repo> {
        repos = lazy { repos() }
        return repos.value
    }

    private fun repos(): List<Repo> {
        val response: String = runBlocking {
            client.get("https://api.github.com/orgs/navikt/teams/tbd/repos?per_page=200") {
                header(HttpHeaders.Authorization, "token $token")
            }.bodyAsText()
        }
        val json = mapper.readTree(response) as ArrayNode
        val repos = json
            .filter { node ->  filters.all { it(node) }}
            .map { Repo.fromJson(it) { name -> commits(name) } }
            .sortRepos()
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

    private fun Iterable<Repo>.sortRepos(): List<Repo> {
        return sortedByDescending { it.commits }
            .sortedByDescending { it.name.startsWith("helse") }
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
                jsonNode["visibility"].asText(),
                commits(name)
            )
        }
    }
}

fun Iterable<Repo>.toJson() = mapper.writeValueAsString(this)