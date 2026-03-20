plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "LLM-Client"
include("agent-memory")
include("mcp-api-server")
include("rag-indexer")