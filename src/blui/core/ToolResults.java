package blui.core;

/** It holds the outcome of an executed file */
public record ToolResults(
String toolName,
String output,
boolean isSuccess,
long durationMs
) {}
