plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "LLM-Client"
include("agent-memory")
include("agent-tasks")
include("mcp-api-server")
include("mcp-notes-server")
include("mcp-weather-server")
include("rag-indexer")
