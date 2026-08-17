package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.PricingRequest;
import com.anushaporter.backend.dto.PricingResponse;
import com.anushaporter.backend.model.DistanceSlab;
import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.model.WeightSlab;
import com.anushaporter.backend.repository.DistanceSlabRepository;
import com.anushaporter.backend.repository.GlobalSettingsRepository;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.repository.PorterServiceRepository;
import com.anushaporter.backend.repository.WeightSlabRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PricingService {

    @Autowired private PricingVehicleRepository vehicleRepo;
    @Autowired private PorterServiceRepository porterServiceRepo;
    @Autowired private DistanceSlabRepository distanceRepo;
    @Autowired private WeightSlabRepository weightRepo;
    @Autowired private GlobalSettingsRepository settingsRepo;

    public PricingResponse calculatePricing(PricingRequest request) {
        String requestedId = request.getVehicleId();

        // 1. Resolve vehicle/service from PorterServiceRepository or PricingVehicleRepository
        PorterService porterService = resolvePorterService(requestedId);
        PricingVehicle vehicle = (porterService == null) ? resolvePricingVehicle(requestedId) : null;

        String vehicleId = "2-wheeler-bike-courier";
        String vehicleName = "2 Wheeler - Bike Courier";
        double baseFare = 100.0;
        double baseKm = 2.0;
        double defaultPerKmRate = 15.0;
        double helperRate = 0.0;

        if (porterService != null) {
            vehicleId = porterService.getServiceId() != null ? porterService.getServiceId() : requestedId;
            vehicleName = porterService.getName() != null ? porterService.getName() : vehicleId;
            baseFare = porterService.getBaseFare() != null ? porterService.getBaseFare() : 100.0;
            baseKm = porterService.getBaseKm() != null ? porterService.getBaseKm() : 2.0;
            defaultPerKmRate = porterService.getPerKmRate() != null ? porterService.getPerKmRate() : 15.0;
            helperRate = porterService.getHelperRate() != null ? porterService.getHelperRate() : 0.0;
        } else if (vehicle != null) {
            vehicleId = vehicle.getVehicleId() != null ? vehicle.getVehicleId() : requestedId;
            vehicleName = vehicle.getName() != null ? vehicle.getName() : vehicleId;
            baseFare = vehicle.getBaseFare() != null ? vehicle.getBaseFare() : 200.0;
            baseKm = 2.0;
            defaultPerKmRate = vehicle.getPricePerKm() != null ? vehicle.getPricePerKm() : 15.0;
            helperRate = 150.0;
        } else if (requestedId != null && !requestedId.isBlank()) {
            vehicleId = requestedId;
            vehicleName = requestedId;
        }

        double distanceKm = request.getDistanceKm() != null ? request.getDistanceKm() : 0.0;
        double weightKg = request.getWeightKg() != null ? request.getWeightKg() : 0.0;
        int helperCount = request.getHelperCount() != null ? request.getHelperCount() : 0;
        double requestWaitingMins = request.getWaitingMins() != null ? request.getWaitingMins() : 0.0;

        // Settings Map
        Map<String, String> settings = new HashMap<>();
        for (var s : settingsRepo.findAll()) {
            if (s.getSettingKey() != null) {
                settings.put(s.getSettingKey(), s.getSettingValue() != null ? s.getSettingValue() : "");
            }
        }

        // Distance Calculation (using Slabs if available, else per km rate after baseKm)
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
            double extraKm = Math.max(0.0, distanceKm - baseKm);
            distanceFare = extraKm * defaultPerKmRate;
        }

        // Weight Calculation
        double weightCharge = 0.0;
        List<WeightSlab> weightSlabs = weightRepo.findByVehicleIdOrderByFromKgAsc(vehicleId);
        if (weightSlabs != null && !weightSlabs.isEmpty()) {
            for (WeightSlab wSlab : weightSlabs) {
                if (weightKg > wSlab.getFromKg() && weightKg <= wSlab.getToKg()) {
                    if (wSlab.getPrice() != null) weightCharge = wSlab.getPrice();
                    break;
                } else if (weightKg > wSlab.getToKg()) {
                    if (wSlab.getPrice() != null) weightCharge = wSlab.getPrice();
                }
            }
        }

        // Helper Charges
        if (porterService == null) {
            try {
                helperRate = Double.parseDouble(settings.getOrDefault(
                    "HELPER_CHARGE_" + vehicleId.toUpperCase().replace("-", "_"), "150.0"));
            } catch (NumberFormatException ignored) {}
        }
        double helperCharge = helperCount * helperRate;

        // Waiting Charges
        double freeWaitingMins = 10.0;
        double waitingRatePerMin = 2.0;
        try {
            freeWaitingMins = Double.parseDouble(settings.getOrDefault("FREE_WAITING_MINS", "10.0"));
            waitingRatePerMin = Double.parseDouble(settings.getOrDefault("WAITING_RATE_PER_MIN", "2.0"));
        } catch (NumberFormatException ignored) {}

        double waitingCharge = 0.0;
        if (requestWaitingMins > freeWaitingMins) {
            waitingCharge = (requestWaitingMins - freeWaitingMins) * waitingRatePerMin;
        }

        // Toll & Fuel
        double tollCharge = request.getTollCharge() != null ? request.getTollCharge() : 0.0;
        double fuelSurcharge = 0.0;
        if ("true".equalsIgnoreCase(settings.getOrDefault("ENABLE_FUEL_SURCHARGE", "false"))) {
            if ("fixed".equalsIgnoreCase(settings.getOrDefault("FUEL_SURCHARGE_TYPE", "percentage"))) {
                fuelSurcharge = Double.parseDouble(settings.getOrDefault("FUEL_SURCHARGE_VALUE", "20.0"));
            } else {
                double pct = Double.parseDouble(settings.getOrDefault("FUEL_SURCHARGE_VALUE", "5.0"));
                fuelSurcharge = (baseFare + distanceFare + weightCharge) * (pct / 100.0);
            }
        }

        // Subtotal & Surge
        double subtotal = baseFare + distanceFare + weightCharge + helperCharge + fuelSurcharge + waitingCharge + tollCharge;

        if ("true".equalsIgnoreCase(settings.getOrDefault("ENABLE_SURGE", "false"))) {
            double surgeMultiplier = Double.parseDouble(settings.getOrDefault("SURGE_MULTIPLIER", "1.3"));
            subtotal = subtotal * surgeMultiplier;
        }

        double discount = 0.0;

        // Platform Fee & GST
        double platformFee = 0.0;
        if ("fixed".equalsIgnoreCase(settings.getOrDefault("PLATFORM_FEE_TYPE", "percentage"))) {
            platformFee = Double.parseDouble(settings.getOrDefault("PLATFORM_FEE_VALUE", "0.0"));
        } else {
            double pct = Double.parseDouble(settings.getOrDefault("PLATFORM_FEE_VALUE", "0.0"));
            platformFee = subtotal * (pct / 100.0);
        }

        double gst = subtotal * 0.18; // 18% GST
        double totalFare = subtotal + platformFee + gst - discount;

        PricingResponse response = new PricingResponse();
        response.setBaseFare(Math.round(baseFare * 100.0) / 100.0);
        response.setDistanceFare(Math.round(distanceFare * 100.0) / 100.0);
        response.setWeightCharge(Math.round(weightCharge * 100.0) / 100.0);
        response.setHelperCharge(Math.round(helperCharge * 100.0) / 100.0);
        response.setHelperChargePerHead(Math.round(helperRate * 100.0) / 100.0);
        response.setFuelCharge(Math.round(fuelSurcharge * 100.0) / 100.0);
        response.setWaitingCharge(Math.round(waitingCharge * 100.0) / 100.0);
        response.setTollCharge(Math.round(tollCharge * 100.0) / 100.0);
        response.setPlatformFee(Math.round(platformFee * 100.0) / 100.0);
        response.setDiscount(Math.round(discount * 100.0) / 100.0);
        response.setGst(Math.round(gst * 100.0) / 100.0);
        response.setTotalFare(Math.round(totalFare * 100.0) / 100.0);
        response.setVehicleId(vehicleId);
        response.setVehicleName(vehicleName);
        response.setHelperCount(helperCount);
        response.setDistanceKm(Math.round(distanceKm * 100.0) / 100.0);
        response.setGstRate(18.0);

        return response;
    }

    private PorterService resolvePorterService(String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            List<PorterService> active = porterServiceRepo.findByIsActiveTrueOrderByDisplayOrderAsc();
            return active.isEmpty() ? null : active.get(0);
        }
        Optional<PorterService> direct = porterServiceRepo.findByServiceId(requestedId);
        if (direct.isPresent()) return direct.get();

        Optional<PorterService> ignoreCase = porterServiceRepo.findFirstByServiceIdIgnoreCase(requestedId);
        if (ignoreCase.isPresent()) return ignoreCase.get();

        String norm = requestedId.toLowerCase().replaceAll("[^a-z0-9]+", "");
        List<PorterService> allServices = porterServiceRepo.findByIsActiveTrueOrderByDisplayOrderAsc();
        for (PorterService s : allServices) {
            String sNorm = s.getServiceId() != null ? s.getServiceId().toLowerCase().replaceAll("[^a-z0-9]+", "") : "";
            if (!sNorm.isEmpty() && (sNorm.equals(norm) || sNorm.contains(norm) || norm.contains(sNorm))) {
                return s;
            }
            String nNorm = s.getName() != null ? s.getName().toLowerCase().replaceAll("[^a-z0-9]+", "") : "";
            if (!nNorm.isEmpty() && (nNorm.equals(norm) || nNorm.contains(norm) || norm.contains(nNorm))) {
                return s;
            }
        }
        return null;
    }

    private PricingVehicle resolvePricingVehicle(String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            List<PricingVehicle> active = vehicleRepo.findByStatus(true);
            return active.isEmpty() ? null : active.get(0);
        }
        PricingVehicle v = vehicleRepo.findByVehicleId(requestedId);
        if (v != null) return v;

        List<PricingVehicle> allVehicles = vehicleRepo.findAll();
        String norm = requestedId.toLowerCase().replaceAll("[^a-z0-9]+", "");
        for (PricingVehicle pv : allVehicles) {
            String vNorm = pv.getVehicleId() != null ? pv.getVehicleId().toLowerCase().replaceAll("[^a-z0-9]+", "") : "";
            if (!vNorm.isEmpty() && (vNorm.equals(norm) || vNorm.contains(norm) || norm.contains(vNorm))) {
                return pv;
            }
        }
        return null;
    }

    public Map<String, Double> estimateAll(PricingRequest request) {
        Map<String, Double> estimates = new HashMap<>();
        List<PorterService> services = porterServiceRepo.findByIsActiveTrueOrderByDisplayOrderAsc();
        if (!services.isEmpty()) {
            for (PorterService service : services) {
                request.setVehicleId(service.getServiceId());
                estimates.put(service.getServiceId(), calculatePricing(request).getTotalFare());
            }
        } else {
            List<PricingVehicle> vehicles = vehicleRepo.findAll();
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
