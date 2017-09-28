package adapter.model

import com.fasterxml.jackson.databind.JsonNode
import shared.Forkee

sealed class Event {
  abstract val type: String
  abstract val repo: Repo
  abstract val created_at: String
  abstract val payload: JsonNode
}

data class ForkEvent(
  override val type: String,
  override val repo: Repo,
  override val created_at: String,
  override val payload: JsonNode) : Event() {

  fun forkee(): Forkee {
    return Forkee(payload.get("forkee").get("html_url").textValue(), "https://github.com/${repo.name}")
  }
}
