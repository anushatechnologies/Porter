package com.anushaporter.backend.model;

public enum PayoutStatus {
    NOT_ELIGIBLE,
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    REVERSED,
    ON_HOLD;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == REVERSED;
    }
}
