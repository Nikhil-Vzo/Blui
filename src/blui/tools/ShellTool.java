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

              //Read output while process executes to prevent pipe buffer deadlock

              StringBuilder output = new StringBuilder();
              try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
              {
                  String line;
                  while ((line = reader.readLine()) !=null) {
                      if(!output.isEmpty()) {
                          output.append(System.lineSeparator());
                      }
                       output.append(line);
                  }
              }

              boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
             if (!finished){
                 process.destroyForcibly();
                 long duration = System.currentTimeMillis() - start;
                 return new ToolResults(
                  name(),
                  "error: Command timed out after" + timeout.toMillis()+ "ms",
                  false,
                  duration
                 );             }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - start;
            boolean success = (exitCode == 0);

            return new ToolResults(name(), output.toString(), success, duration);
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
