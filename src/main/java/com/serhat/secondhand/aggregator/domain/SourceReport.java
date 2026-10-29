package com.serhat.secondhand.aggregator.domain;

public record SourceReport(String id, String name, String type, boolean simulated, String status, int count, long latencyMs) {}
