package com.serhat.secondhand.aggregator.domain;

/**
 * Describes a data source. {@code reliabilityPrior} (0..1) is a-priori trust in the
 * platform itself (e.g. escrow-protected internal marketplace vs. open community chat).
 */
public record SourceInfo(String id, String name, String type, boolean simulated, double reliabilityPrior) {}
