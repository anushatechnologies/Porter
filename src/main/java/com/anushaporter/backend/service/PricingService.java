package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.PricingRequest;
import com.anushaporter.backend.dto.PricingResponse;
import com.anushaporter.backend.model.DistanceSlab;
import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.model.WeightSlab;
import com.anushaporter.backend.repository.DistanceSlabRepository;
import com.anushaporter.backend.repository.GlobalSettingsRepository;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.repository.WeightSlabRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PricingService {

    @Autowired private PricingVehicleRepository vehicleRepo;
    @Autowired private DistanceSlabRepository distanceRepo;
    @Autowired private WeightSlabRepository weightRepo;
    @Autowired private GlobalSettingsRepository settingsRepo;

    public PricingResponse calculatePricing(PricingRequest request) {
        String vehicleId = request.getVehicleId() != null ? request.getVehicleId() : "tata-ace";
        PricingVehicle vehicle = vehicleRepo.findByVehicleId(vehicleId);

        // Fallbacks if vehicle not found
        double baseFare = 200.0;
        if (vehicle != null && vehicle.getBaseFare() != null) {
            baseFare = vehicle.getBaseFare();
        }

        double defaultPerKmRate = 15.0;
        if (vehicle != null && vehicle.getPricePerKm() != null) {
            defaultPerKmRate = vehicle.getPricePerKm();
        }

        double distanceKm = 0.0;
        if (request.getDistanceKm() != null) {
            distanceKm = request.getDistanceKm();
        }

        double weightKg = 0.0;
        if (request.getWeightKg() != null) {
            weightKg = request.getWeightKg();
        }

        int helperCount = 0;
        if (request.getHelperCount() != null) {
            helperCount = request.getHelperCount();
        }

        double requestWaitingMins = 0.0;
        if (request.getWaitingMins() != null) {
            requestWaitingMins = request.getWaitingMins();
        }

        // Settings Map
        Map<String, String> settings = settingsRepo.findAll().stream()
                .collect(Collectors.toMap(s -> s.getSettingKey(), s -> s.getSettingValue()));

        // 1. Distance Calculation (using Slabs if available)
        double distanceFare = 0.0;
        String city = "Hyderabad"; // Default city if not provided
        List<DistanceSlab> slabs = distanceRepo.findByCityAndVehicleIdOrderByFromKmAsc(city, vehicleId);
        
        if (slabs != null && !slabs.isEmpty()) {
            double remainingDistance = distanceKm;
            for (DistanceSlab slab : slabs) {
                if (remainingDistance <= 0) break;
                
                double slabRange = slab.getToKm() - slab.getFromKm();
                double chargeableDistanceInSlab = Math.min(remainingDistance, slabRange);
                
                if (slab.getBaseFare() != null && slab.getFromKm() == 0) {
                    baseFare = slab.getBaseFare(); // Override global base fare for first slab
                    if (slab.getPricePerKm() != null) {
                        distanceFare += chargeableDistanceInSlab * slab.getPricePerKm();
                    }
                } else if (slab.getPricePerKm() != null) {
                    distanceFare += chargeableDistanceInSlab * slab.getPricePerKm();
                }
                
                remainingDistance -= chargeableDistanceInSlab;
            }
        } else {
            // Default flat rate per km after 2km
            distanceFare = distanceKm > 2 ? (distanceKm - 2) * defaultPerKmRate : 0.0;
        }

        // 2. Weight Calculation
        double weightCharge = 0.0;
        List<WeightSlab> weightSlabs = weightRepo.findByVehicleIdOrderByFromKgAsc(vehicleId);
        if (weightSlabs != null && !weightSlabs.isEmpty()) {
            for (WeightSlab wSlab : weightSlabs) {
                if (weightKg > wSlab.getFromKg() && weightKg <= wSlab.getToKg()) {
                    weightCharge = 0.0;
                    if (wSlab.getPrice() != null) {
                        weightCharge = wSlab.getPrice();
                    }
                    break;
                } else if (weightKg > wSlab.getToKg()) {
                    // Check if it's the last slab and we exceed it
                    weightCharge = 0.0;
                    if (wSlab.getPrice() != null) {
                        weightCharge = wSlab.getPrice();
                    }
                }
            }
        }

        // 3. Helper Charges
        double helperRate = Double.parseDouble(settings.getOrDefault("HELPER_CHARGE_" + vehicleId.toUpperCase(), "150.0"));
        double helperCharge = helperCount * helperRate;

        // 4. Waiting Charges
        double freeWaitingMins = Double.parseDouble(settings.getOrDefault("FREE_WAITING_MINS", "10.0"));
        double waitingRatePerMin = Double.parseDouble(settings.getOrDefault("WAITING_RATE_PER_MIN", "2.0"));
        double waitingCharge = 0.0;
        if (requestWaitingMins > freeWaitingMins) {
            waitingCharge = (requestWaitingMins - freeWaitingMins) * waitingRatePerMin;
        }

        // 5. Toll & Fuel
        double tollCharge = 0.0;
        if (request.getTollCharge() != null) {
            tollCharge = request.getTollCharge();
        }
        double fuelSurcharge = 0.0;
        if ("true".equalsIgnoreCase(settings.getOrDefault("ENABLE_FUEL_SURCHARGE", "false"))) {
            if ("fixed".equalsIgnoreCase(settings.getOrDefault("FUEL_SURCHARGE_TYPE", "percentage"))) {
                fuelSurcharge = Double.parseDouble(settings.getOrDefault("FUEL_SURCHARGE_VALUE", "20.0"));
            } else {
                double pct = Double.parseDouble(settings.getOrDefault("FUEL_SURCHARGE_VALUE", "5.0"));
                fuelSurcharge = (baseFare + distanceFare + weightCharge) * (pct / 100.0);
            }
        }

        // 6. Subtotal & Surge
        double subtotal = baseFare + distanceFare + weightCharge + helperCharge + fuelSurcharge + waitingCharge + tollCharge;
        
        if ("true".equalsIgnoreCase(settings.getOrDefault("ENABLE_SURGE", "false"))) {
            double surgeMultiplier = Double.parseDouble(settings.getOrDefault("SURGE_MULTIPLIER", "1.3"));
            subtotal = subtotal * surgeMultiplier;
        }

        double discount = 0.0;
        
        // 7. Platform Fee & GST
        double platformFee;
        if ("fixed".equalsIgnoreCase(settings.getOrDefault("PLATFORM_FEE_TYPE", "percentage"))) {
            platformFee = Double.parseDouble(settings.getOrDefault("PLATFORM_FEE_VALUE", "10.0"));
        } else {
            double pct = Double.parseDouble(settings.getOrDefault("PLATFORM_FEE_VALUE", "2.0"));
            platformFee = subtotal * (pct / 100.0);
        }

        double gst = (subtotal + platformFee) * 0.05; // 5% GST
        double totalFare = subtotal + platformFee + gst - discount;

        PricingResponse response = new PricingResponse();
        response.setBaseFare(Math.round(baseFare * 100.0) / 100.0);
        response.setDistanceFare(Math.round(distanceFare * 100.0) / 100.0);
        response.setWeightCharge(Math.round(weightCharge * 100.0) / 100.0);
        response.setHelperCharge(Math.round(helperCharge * 100.0) / 100.0);
        response.setFuelCharge(Math.round(fuelSurcharge * 100.0) / 100.0);
        response.setWaitingCharge(Math.round(waitingCharge * 100.0) / 100.0);
        response.setTollCharge(Math.round(tollCharge * 100.0) / 100.0);
        response.setPlatformFee(Math.round(platformFee * 100.0) / 100.0);
        response.setDiscount(Math.round(discount * 100.0) / 100.0);
        response.setGst(Math.round(gst * 100.0) / 100.0);
        response.setTotalFare(Math.round(totalFare * 100.0) / 100.0);

        return response;
    }

    public Map<String, Double> estimateAll(PricingRequest request) {
        Map<String, Double> estimates = new HashMap<>();
        List<PricingVehicle> vehicles = vehicleRepo.findAll();
        if (vehicles.isEmpty()) {
            // Fallback for empty DB
            String[] defaults = {"three-wheeler", "tata-ace", "pickup-8ft", "tata-407"};
            for (String v : defaults) {
                request.setVehicleId(v);
                estimates.put(v, calculatePricing(request).getTotalFare());
            }
        } else {
            for (PricingVehicle vehicle : vehicles) {
                if (Boolean.TRUE.equals(vehicle.getStatus())) {
                    request.setVehicleId(vehicle.getVehicleId());
                    estimates.put(vehicle.getVehicleId(), calculatePricing(request).getTotalFare());
                }
            }
        }
        return estimates;
    }
}
