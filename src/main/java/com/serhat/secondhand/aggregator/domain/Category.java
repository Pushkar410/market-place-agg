package com.serhat.secondhand.aggregator.domain;

/** Canonical categories shared by every source after normalization. */
public enum Category {
    ELECTRONICS, BOOKS, FURNITURE, VEHICLES, HOUSING, SPORTS, CLOTHING, SERVICES, OTHER;

    public static Category parse(String value) {
        if (value == null) return null;
        try {
            return Category.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
