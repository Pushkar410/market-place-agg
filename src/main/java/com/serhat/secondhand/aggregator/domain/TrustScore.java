package com.serhat.secondhand.aggregator.domain;

import java.util.List;

public record TrustScore(int score, Level level, List<Signal> signals, List<String> riskFlags) {

    public enum Level { HIGH, MEDIUM, LOW }

    /** One explainable component of the score. */
    public record Signal(String name, double points, double max, String detail) {}
}
