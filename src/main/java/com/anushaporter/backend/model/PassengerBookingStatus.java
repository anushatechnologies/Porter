package com.anushaporter.backend.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Strict state machine for Passenger Car Bookings.
 */
public enum PassengerBookingStatus {
    REQUESTED,
    FARE_CONFIRMED,
    DRIVER_SEARCHING,
    DRIVER_ASSIGNED,
    DRIVER_ACCEPTED,
    DRIVER_ARRIVING,
    DRIVER_ARRIVED,
    TRIP_STARTED,
    TRIP_COMPLETED,
    CANCELLED_BY_CUSTOMER,
    CANCELLED_BY_DRIVER,
    CANCELLED_BY_ADMIN,
    PAYMENT_FAILED,
    NO_DRIVER_AVAILABLE,
    EXPIRED;

    private static final Map<PassengerBookingStatus, Set<PassengerBookingStatus>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(REQUESTED, EnumSet.of(FARE_CONFIRMED, EXPIRED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_ADMIN)),
            Map.entry(FARE_CONFIRMED, EnumSet.of(DRIVER_SEARCHING, DRIVER_ASSIGNED, PAYMENT_FAILED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_ADMIN, EXPIRED)),
            Map.entry(DRIVER_SEARCHING, EnumSet.of(DRIVER_ASSIGNED, NO_DRIVER_AVAILABLE, CANCELLED_BY_CUSTOMER, CANCELLED_BY_ADMIN)),
            Map.entry(DRIVER_ASSIGNED, EnumSet.of(DRIVER_ACCEPTED, DRIVER_SEARCHING, CANCELLED_BY_CUSTOMER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN)),
            Map.entry(DRIVER_ACCEPTED, EnumSet.of(DRIVER_ARRIVING, CANCELLED_BY_CUSTOMER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN)),
            Map.entry(DRIVER_ARRIVING, EnumSet.of(DRIVER_ARRIVED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN)),
            Map.entry(DRIVER_ARRIVED, EnumSet.of(TRIP_STARTED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN)),
            Map.entry(TRIP_STARTED, EnumSet.of(TRIP_COMPLETED, CANCELLED_BY_ADMIN)),
            Map.entry(TRIP_COMPLETED, EnumSet.noneOf(PassengerBookingStatus.class)),
            Map.entry(CANCELLED_BY_CUSTOMER, EnumSet.noneOf(PassengerBookingStatus.class)),
            Map.entry(CANCELLED_BY_DRIVER, EnumSet.of(DRIVER_SEARCHING, CANCELLED_BY_ADMIN)),
            Map.entry(CANCELLED_BY_ADMIN, EnumSet.noneOf(PassengerBookingStatus.class)),
            Map.entry(PAYMENT_FAILED, EnumSet.of(FARE_CONFIRMED, EXPIRED, CANCELLED_BY_CUSTOMER)),
            Map.entry(NO_DRIVER_AVAILABLE, EnumSet.of(DRIVER_SEARCHING, CANCELLED_BY_CUSTOMER, CANCELLED_BY_ADMIN)),
            Map.entry(EXPIRED, EnumSet.noneOf(PassengerBookingStatus.class))
    );

    public boolean canTransitionTo(PassengerBookingStatus next) {
        if (this == next) {
            return true;
        }
        Set<PassengerBookingStatus> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(next);
    }

    public boolean isTerminal() {
        return this == TRIP_COMPLETED
                || this == CANCELLED_BY_CUSTOMER
                || this == CANCELLED_BY_DRIVER
                || this == CANCELLED_BY_ADMIN
                || this == EXPIRED;
    }
}
