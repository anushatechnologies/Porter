package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.PricingRequest;
import com.anushaporter.backend.dto.PricingResponse;
import com.anushaporter.backend.dto.VehicleRecommendationRequest;
import com.anushaporter.backend.dto.VehicleRecommendationResponse;
import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.VehicleTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class VehicleRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(VehicleRecommendationService.class);

    @Autowired(required = false)
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private PricingService pricingService;

    public VehicleRecommendationResponse recommendVehicle(VehicleRecommendationRequest request) {
        VehicleRecommendationResponse response = new VehicleRecommendationResponse();
        List<VehicleRecommendationResponse.VehicleOption> options = new ArrayList<>();

        double weight = request.getWeightKg() != null ? request.getWeightKg() : 10.0;
        double distance = request.getDistanceKm() != null ? request.getDistanceKm() : 5.0;
        int helpers = request.getHelperCount() != null ? request.getHelperCount() : 0;
        String goodsCategory = request.getGoodsCategory() != null ? request.getGoodsCategory().toLowerCase() : "general";
        String houseSize = request.getHouseSize() != null ? request.getHouseSize().toLowerCase() : "";

        // Fetch vehicle types from repository or provide defaults
        List<VehicleType> availableVehicles = (vehicleTypeRepository != null)
                ? vehicleTypeRepository.findByStatusOrderByPriorityAsc("active")
                : List.of();

        if (availableVehicles.isEmpty()) {
            availableVehicles = getDefaultVehicleTypes();
        }

        // Evaluate all vehicle options
        for (VehicleType v : availableVehicles) {
            int capacity = v.getCapacityKg() != null ? v.getCapacityKg() : 500;
            boolean suitable = (weight <= capacity);

            // Special category constraints
            if (goodsCategory.contains("house") || houseSize.contains("bhk") || goodsCategory.contains("shift")) {
                if (capacity < 750) suitable = false;
            }

            PricingRequest pricingReq = new PricingRequest();
            pricingReq.setVehicleId(v.getId() != null ? v.getId() : v.getType());
            pricingReq.setDistanceKm(distance);
            pricingReq.setWeightKg(weight);
            pricingReq.setHelperCount(helpers);

            double estimatedFare = 0.0;
            try {
                PricingResponse pr = pricingService.calculatePricing(pricingReq);
                if (pr != null && pr.getTotalFare() != null) {
                    estimatedFare = pr.getTotalFare();
                }
            } catch (Exception e) {
                double base = v.getBaseFare() != null ? v.getBaseFare() : 150.0;
                double perKm = v.getPerKmRate() != null ? v.getPerKmRate() : 15.0;
                estimatedFare = base + (Math.max(0, distance - 1.0) * perKm);
            }

            VehicleRecommendationResponse.VehicleOption option = new VehicleRecommendationResponse.VehicleOption();
            option.setVehicleId(v.getId() != null ? v.getId() : v.getType());
            option.setVehicleName(v.getName() != null ? v.getName() : option.getVehicleId());
            option.setCapacityKg(capacity);
            option.setDimensions(v.getDimensions() != null ? v.getDimensions() : "Standard load space");
            option.setEstimatedFare(Math.round(estimatedFare * 100.0) / 100.0);
            option.setSuitable(suitable);

            options.add(option);
        }

        // Sort suitable options by capacity ascending (most cost-effective capable vehicle first)
        List<VehicleRecommendationResponse.VehicleOption> suitableOptions = options.stream()
                .filter(VehicleRecommendationResponse.VehicleOption::isSuitable)
                .sorted(Comparator.comparingInt(VehicleRecommendationResponse.VehicleOption::getCapacityKg))
                .toList();

        VehicleRecommendationResponse.VehicleOption bestMatch;
        if (!suitableOptions.isEmpty()) {
            bestMatch = suitableOptions.get(0);
            response.setReason("Optimal capacity for payload of " + weight + " kg.");
        } else if (!options.isEmpty()) {
            // Fallback to highest capacity
            bestMatch = options.stream().max(Comparator.comparingInt(VehicleRecommendationResponse.VehicleOption::getCapacityKg)).orElse(options.get(0));
            response.setReason("Payload exceeds standard single trip capacity; recommended largest available vehicle.");
        } else {
            bestMatch = new VehicleRecommendationResponse.VehicleOption();
            bestMatch.setVehicleId("tata-ace");
            bestMatch.setVehicleName("Tata Ace");
            bestMatch.setCapacityKg(750);
            bestMatch.setEstimatedFare(250.0);
            response.setReason("Standard light commercial vehicle.");
        }

        response.setSuccess(true);
        response.setRecommendedVehicleId(bestMatch.getVehicleId());
        response.setRecommendedVehicleName(bestMatch.getVehicleName());
        response.setCapacityKg(bestMatch.getCapacityKg());
        response.setDimensions(bestMatch.getDimensions());
        response.setEstimatedFare(bestMatch.getEstimatedFare());
        response.setAlternativeOptions(options);

        return response;
    }

    private List<VehicleType> getDefaultVehicleTypes() {
        List<VehicleType> list = new ArrayList<>();

        VehicleType bike = new VehicleType();
        bike.setId("1");
        bike.setType("two_wheeler");
        bike.setName("2 Wheeler");
        bike.setCapacityKg(20);
        bike.setDimensions("Up to 20kg parcel/document");
        bike.setBaseFare(40.0);
        bike.setPerKmRate(12.0);
        list.add(bike);

        VehicleType threeWheeler = new VehicleType();
        threeWheeler.setId("2");
        threeWheeler.setType("three_wheeler");
        threeWheeler.setName("3 Wheeler");
        threeWheeler.setCapacityKg(500);
        threeWheeler.setDimensions("5ft x 3.5ft x 4ft");
        threeWheeler.setBaseFare(120.0);
        threeWheeler.setPerKmRate(18.0);
        list.add(threeWheeler);

        VehicleType tataAce = new VehicleType();
        tataAce.setId("3");
        tataAce.setType("tata_ace");
        tataAce.setName("Tata Ace");
        tataAce.setCapacityKg(750);
        tataAce.setDimensions("7ft x 4.5ft x 5ft");
        tataAce.setBaseFare(220.0);
        tataAce.setPerKmRate(24.0);
        list.add(tataAce);

        VehicleType pickup8ft = new VehicleType();
        pickup8ft.setId("4");
        pickup8ft.setType("pickup_8ft");
        pickup8ft.setName("Pickup 8ft");
        pickup8ft.setCapacityKg(1200);
        pickup8ft.setDimensions("8ft x 5ft x 5.5ft");
        pickup8ft.setBaseFare(320.0);
        pickup8ft.setPerKmRate(30.0);
        list.add(pickup8ft);

        return list;
    }
}
