package com.serhat.secondhand.aggregator.multiagent.langchain;

import java.util.Map;

/**
 * LangChain-compatible Tool interface.
 * Represents a tool that a LangChain ReAct agent can invoke.
 */
public interface LangChainTool {
    String name();
    String description();
    String execute(Map<String, Object> input);
}
