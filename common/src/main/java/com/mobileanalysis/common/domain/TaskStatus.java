package com.mobileanalysis.common.domain;

public enum TaskStatus {
    PENDING,
    DISPATCHED,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
