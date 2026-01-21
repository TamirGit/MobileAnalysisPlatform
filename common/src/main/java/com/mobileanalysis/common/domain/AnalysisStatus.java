package com.mobileanalysis.common.domain;

public enum AnalysisStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
