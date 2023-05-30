package com.here.xyz.events;


public enum IfRowLock {
    WAIT,
    ABORT;

    public static IfRowLock of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
