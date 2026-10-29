package com.serhat.secondhand.aggregator.llm;

/** Minimal seam over "an LLM that turns a prompt into text", so agent steps can be unit-tested with a fake. */
public interface LlmTextGenerator {

    /** True only when a real model is configured and enabled. */
    boolean isAvailable();

    /** Returns the model's text; may throw on network / quota errors (callers must fall back). */
    String generate(String prompt);
}
