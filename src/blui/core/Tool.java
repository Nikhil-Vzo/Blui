package blui.core;

/**
  Represents an executable capability available to the agent.
 */

@FunctionalInterface
public interface Tool {

	ToolResults execute(String input) throws Exception;
	default String name()
	{
	  return getClass().getSimpleName();
     }
}
