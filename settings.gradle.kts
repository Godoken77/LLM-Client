plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "LLM-Client"
include("mcp-api-server")
include("mcp-notes-server")
include("mcp-weather-server")
include("rag-indexer")
