package com.anushaporter.backend.service;

import com.anushaporter.backend.model.Driver;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DriverRankingService {

    public static class RankedDriver {
        private final Driver driver;
        private final double distanceKm;
        private final double compositeScore;

        public RankedDriver(Driver driver, double distanceKm, double compositeScore) {
            this.driver = driver;
            this.distanceKm = distanceKm;
            this.compositeScore = compositeScore;
        }

        public Driver getDriver() { return driver; }
        public double getDistanceKm() { return distanceKm; }
        public double getCompositeScore() { return compositeScore; }
    }

    public List<RankedDriver> rankDrivers(List<Driver> drivers, double pickupLat, double pickupLng, double maxRadiusKm, int topN) {
        return drivers.stream()
                .map(d -> {
                    double dist = calculateHaversineDistanceKm(pickupLat, pickupLng, d.getLatitude(), d.getLongitude());
                    double score = calculateScore(d, dist, maxRadiusKm);
                    return new RankedDriver(d, dist, score);
                })
                .filter(rd -> rd.getDistanceKm() <= maxRadiusKm)
                .sorted(Comparator.comparingDouble(RankedDriver::getCompositeScore).reversed())
                .limit(topN > 0 ? topN : 3)
                .toList();
    }

    public double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_KM = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = EARTH_RADIUS_KM * c;
        return Math.round(distance * 100.0) / 100.0;
    }

    private double calculateScore(Driver driver, double distanceKm, double maxRadiusKm) {
        // Distance Score: 0 to 50 pts (closer to 0 km = 50 pts)
        double distanceScore = Math.max(0.0, (1.0 - (distanceKm / Math.max(1.0, maxRadiusKm)))) * 50.0;

        // Rating Score: 0 to 30 pts (5.0 rating = 30 pts)
        double rating = 4.5;
        if (driver.getRating() != null) {
            try {
                rating = Double.parseDouble(driver.getRating());
            } catch (NumberFormatException ignored) {}
        }
        double ratingScore = Math.min(5.0, Math.max(1.0, rating)) / 5.0 * 30.0;

        // Experience Score: 0 to 20 pts (50+ trips = 20 pts)
        int trips = driver.getTrips() != null ? driver.getTrips() : 0;
        double experienceScore = Math.min(20.0, (trips / 50.0) * 20.0);

        return distanceScore + ratingScore + experienceScore;
    }
}
