package com.anushaporter.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PassengerCapacityExceededException extends RuntimeException {
    public PassengerCapacityExceededException(String message) {
        super(message);
    }

    public static PassengerCapacityExceededException defaultMessage() {
        return new PassengerCapacityExceededException("Please select a larger vehicle for this number of passengers.");
    }
}
