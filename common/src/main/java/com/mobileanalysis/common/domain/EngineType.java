package com.mobileanalysis.common.domain;

public enum EngineType {
    STATIC_ANALYSIS,
    DYNAMIC_ANALYSIS,
    DECOMPILER,
    SIGNATURE_CHECK;

    public String getTopicName() {
        return this.name().toLowerCase().replace('_', '-') + "-tasks";
    }
}
