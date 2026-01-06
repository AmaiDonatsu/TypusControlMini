package com.example.typuscontrolmini

data class McpRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Any? = null,
    val id: Any? = null
)

data class McpResponse(
    val jsonrpc: String = "2.0",
    val result: Any? = null,
    val error: McpError? = null,
    val id: Any? = null
)

data class McpError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)
