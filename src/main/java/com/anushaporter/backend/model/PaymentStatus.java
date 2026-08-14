package com.anushaporter.backend.model;

public enum PaymentStatus {
    CREATED,
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    PARTIALLY_REFUNDED,
    DISPUTED;

    public boolean canTransitionTo(PaymentStatus next) {
        if (this == next) return true;
        return switch (this) {
            case CREATED -> next == PENDING || next == PROCESSING || next == CANCELLED;
            case PENDING -> next == PROCESSING || next == SUCCESS || next == FAILED || next == EXPIRED || next == CANCELLED;
            case PROCESSING -> next == SUCCESS || next == FAILED || next == EXPIRED;
            case SUCCESS -> next == REFUND_PENDING || next == REFUNDED || next == PARTIALLY_REFUNDED || next == DISPUTED;
            case REFUND_PENDING -> next == REFUNDED || next == PARTIALLY_REFUNDED || next == SUCCESS;
            case FAILED, EXPIRED, CANCELLED, REFUNDED -> false;
            case PARTIALLY_REFUNDED -> next == REFUNDED || next == PARTIALLY_REFUNDED || next == DISPUTED;
            case DISPUTED -> next == REFUNDED || next == SUCCESS;
        };
    }
}
