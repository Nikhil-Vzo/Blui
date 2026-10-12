package blui.tools;

import blui.core.Tool;
import blui.core.ToolResults;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class ShellTool implements Tool
{
 private final Duration timeout;

 public ShellTool()
 {
     this(Duration.ofSeconds(10));
 }
 public ShellTool(Duration timeout)
 {
     this.timeout = timeout != null ? timeout : Duration.ofSeconds(10);
 }

 @Override
 public String name() {
     return "ShellTool";
 }
 @Override
 public ToolResults execute(String command)
 {
     long start = System.currentTimeMillis();

     if (command == null ||command.isBlank())
     {
         return new ToolResults(name(), "Error: Command cannot be empty", false, 0);
     }

     Process process = null;
     try {
         boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
         ProcessBuilder pb = isWindows
              ? new ProcessBuilder("cmd.exe", "/c", command)
              : new ProcessBuilder("sh", "-c", command);

              pb.redirectErrorStream(true);
              process = pb.start();

              StringBuilder output = new StringBuilder();
              final Process runningProcess = process;
              Thread outputDrainer = Thread.ofVirtual().start(() -> {
                  try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
                      String line;
                      while ((line = reader.readLine()) != null) {
                          synchronized (output) {
                              if (!output.isEmpty()) {
                                  output.append(System.lineSeparator());
                              }
                              if (output.length() < 1_000_000) {
                                  output.append(line);
                              }
                          }
                      }
                  } catch (Exception ignored) {}
              });

              boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
              if (!finished) {
                  process.destroyForcibly();
                  outputDrainer.interrupt();
                  long duration = System.currentTimeMillis() - start;
                  return new ToolResults(
                      name(),
                      "Error: Command timed out after " + timeout.toMillis() + "ms",
                      false,
                      duration
                  );
              }

              try {
                  outputDrainer.join(Duration.ofMillis(500));
              } catch (InterruptedException ignored) {}

              int exitCode = process.exitValue();
              long duration = System.currentTimeMillis() - start;
              boolean success = (exitCode == 0);

              String resultOutput;
              synchronized (output) {
                  resultOutput = output.toString();
              }
              return new ToolResults(name(), resultOutput, success, duration);
 }
catch (Exception e)
{
    if (process != null && process.isAlive()) {
        process.destroyForcibly();
    }
    long duration = System.currentTimeMillis() - start;
    return new ToolResults(name(), "Execution failed:"+ e.getMessage(), false, duration);
}
}
}
