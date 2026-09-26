package com.anushaporter.backend.service;

import com.anushaporter.backend.model.PassengerVehicleCategory;
import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.PassengerVehicleCategoryRepository;
import com.anushaporter.backend.repository.PorterServiceRepository;
import com.anushaporter.backend.repository.VehicleTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * FleetSyncService
 *
 * Keeps Our Services (delivery/freight/packers) and Passenger Taxi rides
 * synchronized and strictly categorized for both Drivers and Customers:
 *
 * 1. Category "two_wheeler", "vehicle", "truck", "packers" -> Assigned OUR_SERVICES
 * 2. Category "passenger", "cab", "taxi" -> Assigned PASSENGER
 * 3. Shared categories ("both", "all", "shared") -> Assigned BOTH
 *
 * Driver Filtering Guarantees:
 * - Driver selecting "Our Services": gets only OUR_SERVICES & BOTH. Passenger rides are excluded.
 * - Driver selecting "Passenger Taxi": gets only PASSENGER & BOTH. Delivery trucks are excluded.
 */
@Service
public class FleetSyncService {

    private static final Logger log = LoggerFactory.getLogger(FleetSyncService.class);

    @Autowired(required = false)
    private PorterServiceRepository porterServiceRepository;

    @Autowired(required = false)
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired(required = false)
    private PassengerVehicleCategoryRepository passengerVehicleCategoryRepository;

    /**
     * Complete synchronization of both Our Services and Passenger fleet categories.
     */
    public void syncAll() {
        syncAllFromPorterServices();
        syncAllFromPassengerCategories();
    }

    /**
     * Synchronize all delivery/freight services from PorterService (services table) into VehicleType.
     */
    public void syncAllFromPorterServices() {
        if (porterServiceRepository == null || vehicleTypeRepository == null) return;
        try {
            List<PorterService> services = porterServiceRepository.findAll();
            if (services == null || services.isEmpty()) return;

            for (PorterService s : services) {
                syncPorterService(s);
            }
        } catch (Exception e) {
            log.warn("Error during syncAllFromPorterServices: {}", e.getMessage());
        }
    }

    /**
     * Synchronize a single PorterService into VehicleType.
     */
    public VehicleType syncPorterService(PorterService s) {
        if (s == null || vehicleTypeRepository == null) return null;
        if (isNonVehicleArtifact(s.getName(), s.getServiceId())) {
            String svcId = s.getServiceId() != null && !s.getServiceId().isBlank()
                    ? s.getServiceId().trim()
                    : (s.getId() != null ? String.valueOf(s.getId()) : s.getName());
            if (svcId != null) {
                vehicleTypeRepository.findById(svcId).ifPresent(vehicleTypeRepository::delete);
            }
            return null;
        }
        try {
            String cat = s.getCategory() != null ? s.getCategory().toLowerCase().trim() : "";
            String name = s.getName() != null ? s.getName().toLowerCase().trim() : "";

            // Determine service type assignment
            String assignedServiceType;
            if (cat.contains("both") || cat.contains("shared")) {
                assignedServiceType = "BOTH";
            } else if (cat.contains("passenger") || cat.contains("cab") || cat.contains("taxi")
                    || name.contains("passenger") || name.contains("cab taxi")) {
                assignedServiceType = "PASSENGER";
            } else {
                // "two_wheeler", "vehicle", "truck", "packers", "freight", "courier" -> OUR_SERVICES
                assignedServiceType = "OUR_SERVICES";
            }

            String svcId = s.getServiceId() != null && !s.getServiceId().isBlank()
                    ? s.getServiceId().trim()
                    : (s.getId() != null ? String.valueOf(s.getId()) : s.getName());

            Optional<VehicleType> existingOpt = vehicleTypeRepository.findById(svcId);
            if (existingOpt.isEmpty()) {
                existingOpt = vehicleTypeRepository.findByType(svcId);
            }
            if (existingOpt.isEmpty()) {
                String legacyId = resolveLegacyDeliveryId(svcId);
                if (legacyId != null) {
                    existingOpt = vehicleTypeRepository.findById(legacyId);
                }
            }
            if (existingOpt.isEmpty() && s.getName() != null && !s.getName().isBlank()) {
                final String sName = s.getName().trim();
                existingOpt = vehicleTypeRepository.findAll().stream()
                        .filter(v -> v.getName() != null && v.getName().trim().equalsIgnoreCase(sName))
                        .findFirst();
            }

            VehicleType vt;
            if (existingOpt.isEmpty()) {
                vt = new VehicleType();
                vt.setId(svcId);
                vt.setName(s.getName() != null && !s.getName().isBlank() ? s.getName() : svcId);
                vt.setDisplayName(s.getName());
                vt.setType(s.getCategory() != null && !s.getCategory().isBlank() ? s.getCategory() : svcId);
                vt.setDescription(s.getDescription() != null ? s.getDescription() : s.getSubtitle());
                vt.setCapacity(s.getCapacityLabel() != null ? s.getCapacityLabel() : (s.getCapacityKg() != null ? "Up to " + s.getCapacityKg() + " kg" : ""));
                vt.setCapacityKg(s.getCapacityKg() != null ? s.getCapacityKg() : 20);
                vt.setDimensions(s.getDimensions() != null ? s.getDimensions() : "");
                vt.setIconName(resolveDeliveryIcon(s.getCategory(), s.getName()));
                vt.setImageUrl(s.getIconUrl() != null && !s.getIconUrl().isBlank() ? s.getIconUrl() : "");
                vt.setBaseFare(s.getBaseFare() != null ? s.getBaseFare() : 40.0);
                vt.setBaseKm(s.getBaseKm() != null ? s.getBaseKm() : 1.0);
                vt.setPerKmRate(s.getPerKmRate() != null ? s.getPerKmRate() : 12.0);
                vt.setHelperRate(s.getHelperRate() != null ? s.getHelperRate() : 0.0);
                vt.setStatus(Boolean.FALSE.equals(s.getIsActive()) ? "inactive" : "active");
                vt.setPriority(s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
                vt.setServiceType(assignedServiceType);
                vt.setCustomerAppVisible(Boolean.TRUE.equals(s.getCustomerAppVisible()));
                vt.setAvailableCities(s.getAvailableCities() != null ? s.getAvailableCities() : "ALL");
            } else {
                vt = existingOpt.get();
                if (s.getName() != null && !s.getName().isBlank()) {
                    vt.setName(s.getName());
                    vt.setDisplayName(s.getName());
                }
                if (s.getDescription() != null && !s.getDescription().isBlank()) {
                    vt.setDescription(s.getDescription());
                } else if (s.getSubtitle() != null && !s.getSubtitle().isBlank() && (vt.getDescription() == null || vt.getDescription().isBlank())) {
                    vt.setDescription(s.getSubtitle());
                }
                if (s.getCapacityLabel() != null && !s.getCapacityLabel().isBlank()) {
                    vt.setCapacity(s.getCapacityLabel());
                }
                if (s.getCapacityKg() != null) {
                    vt.setCapacityKg(s.getCapacityKg());
                }
                if (s.getDimensions() != null && !s.getDimensions().isBlank()) {
                    vt.setDimensions(s.getDimensions());
                }
                if (s.getIconUrl() != null && !s.getIconUrl().isBlank()) {
                    vt.setImageUrl(s.getIconUrl());
                }
                if (s.getBaseFare() != null) {
                    vt.setBaseFare(s.getBaseFare());
                }
                if (s.getBaseKm() != null) {
                    vt.setBaseKm(s.getBaseKm());
                }
                if (s.getPerKmRate() != null) {
                    vt.setPerKmRate(s.getPerKmRate());
                }
                if (s.getHelperRate() != null) {
                    vt.setHelperRate(s.getHelperRate());
                }
                if (s.getDisplayOrder() != null) {
                    vt.setPriority(s.getDisplayOrder());
                }
                if (s.getCustomerAppVisible() != null) {
                    vt.setCustomerAppVisible(s.getCustomerAppVisible());
                }
                if (s.getAvailableCities() != null) {
                    vt.setAvailableCities(s.getAvailableCities());
                }
                vt.setStatus(Boolean.FALSE.equals(s.getIsActive()) ? "inactive" : "active");
                // Only override serviceType if it wasn't already configured
                if (vt.getServiceType() == null || vt.getServiceType().isBlank()) {
                    vt.setServiceType(assignedServiceType);
                }
            }

            return vehicleTypeRepository.save(vt);
        } catch (Exception e) {
            log.warn("Failed to sync PorterService ID {}: {}", s != null ? s.getServiceId() : "null", e.getMessage());
            return null;
        }
    }

    /**
     * When a PorterService is deleted by Admin, deactivate the corresponding VehicleType.
     */
    public void deletePorterService(PorterService s) {
        if (s == null || vehicleTypeRepository == null) return;
        try {
            String svcId = s.getServiceId() != null && !s.getServiceId().isBlank()
                    ? s.getServiceId().trim()
                    : (s.getId() != null ? String.valueOf(s.getId()) : s.getName());

            vehicleTypeRepository.findById(svcId).ifPresent(vt -> {
                vt.setStatus("inactive");
                vehicleTypeRepository.save(vt);
            });
        } catch (Exception e) {
            log.warn("Failed to deactivate VehicleType on PorterService delete: {}", e.getMessage());
        }
    }

    /**
     * Synchronize all passenger vehicle categories into VehicleType.
     */
    public void syncAllFromPassengerCategories() {
        if (passengerVehicleCategoryRepository == null || vehicleTypeRepository == null) return;
        try {
            List<PassengerVehicleCategory> categories = passengerVehicleCategoryRepository.findAll();
            if (categories == null || categories.isEmpty()) return;

            for (PassengerVehicleCategory c : categories) {
                syncPassengerCategory(c);
            }
        } catch (Exception e) {
            log.warn("Error during syncAllFromPassengerCategories: {}", e.getMessage());
        }
    }

    /**
     * Synchronize a single PassengerVehicleCategory into VehicleType.
     */
    public VehicleType syncPassengerCategory(PassengerVehicleCategory c) {
        if (c == null || vehicleTypeRepository == null) return null;
        if (isNonVehicleArtifact(c.getDisplayName(), c.getCategoryCode())) {
            String code = c.getCategoryCode() != null ? c.getCategoryCode().trim().toUpperCase() : "";
            if (!code.isBlank()) {
                vehicleTypeRepository.findById(code).ifPresent(vehicleTypeRepository::delete);
            }
            return null;
        }
        try {
            String code = c.getCategoryCode() != null ? c.getCategoryCode().trim().toUpperCase() : "";
            if (code.isBlank()) return null;

            Optional<VehicleType> existingOpt = vehicleTypeRepository.findById(code);
            if (existingOpt.isEmpty()) {
                existingOpt = vehicleTypeRepository.findByType(code);
            }
            if (existingOpt.isEmpty()) {
                String legacyPassengerId = resolveLegacyPassengerId(code);
                if (legacyPassengerId != null) {
                    existingOpt = vehicleTypeRepository.findById(legacyPassengerId);
                }
            }

            VehicleType vt;
            if (existingOpt.isEmpty()) {
                vt = new VehicleType();
                vt.setId(code);
                vt.setType(code);
                vt.setName(c.getDisplayName() != null ? c.getDisplayName() : code);
                vt.setDisplayName(c.getDisplayName() != null ? c.getDisplayName() : code);
                vt.setDescription(c.getDescription() != null ? c.getDescription() : "");
                vt.setCapacity(c.getPassengerCapacity() != null ? c.getPassengerCapacity() + " Seats" : "4 Seats");
                vt.setCapacityKg(c.getLuggageCapacity() != null ? c.getLuggageCapacity() * 20 : 50);
                vt.setMaxPassengers(c.getPassengerCapacity() != null ? c.getPassengerCapacity() : 4);
                vt.setMaxLuggage(c.getLuggageCapacity() != null ? c.getLuggageCapacity() : 2);
                vt.setIconName(resolvePassengerIcon(code));
                vt.setImageUrl(c.getImageUrl() != null ? c.getImageUrl() : "");
                vt.setBaseFare(c.getBaseFare() != null ? c.getBaseFare().doubleValue() : 50.0);
                vt.setBaseKm(c.getMinimumKm() != null ? c.getMinimumKm().doubleValue() : 1.0);
                vt.setPerKmRate(c.getPerKmRate() != null ? c.getPerKmRate().doubleValue() : 15.0);
                vt.setMinFare(c.getMinimumFare() != null ? c.getMinimumFare().doubleValue() : 50.0);
                vt.setDriverAllowance(c.getDriverAllowance() != null ? c.getDriverAllowance().doubleValue() : 0.0);
                vt.setStatus(Boolean.FALSE.equals(c.getActive()) ? "inactive" : "active");
                vt.setPriority(c.getDisplayOrder() != null ? c.getDisplayOrder() : 1);
                vt.setServiceType("PASSENGER");
            } else {
                vt = existingOpt.get();
                if (c.getDisplayName() != null && !c.getDisplayName().isBlank()) {
                    vt.setName(c.getDisplayName());
                    vt.setDisplayName(c.getDisplayName());
                }
                if (c.getDescription() != null) {
                    vt.setDescription(c.getDescription());
                }
                if (c.getPassengerCapacity() != null) {
                    vt.setMaxPassengers(c.getPassengerCapacity());
                    vt.setCapacity(c.getPassengerCapacity() + " Seats");
                }
                if (c.getLuggageCapacity() != null) {
                    vt.setMaxLuggage(c.getLuggageCapacity());
                    vt.setCapacityKg(c.getLuggageCapacity() * 20);
                }
                if (c.getImageUrl() != null && !c.getImageUrl().isBlank()) {
                    vt.setImageUrl(c.getImageUrl());
                }
                if (c.getBaseFare() != null) {
                    vt.setBaseFare(c.getBaseFare().doubleValue());
                }
                if (c.getMinimumKm() != null) {
                    vt.setBaseKm(c.getMinimumKm().doubleValue());
                }
                if (c.getPerKmRate() != null) {
                    vt.setPerKmRate(c.getPerKmRate().doubleValue());
                }
                if (c.getMinimumFare() != null) {
                    vt.setMinFare(c.getMinimumFare().doubleValue());
                }
                if (c.getDriverAllowance() != null) {
                    vt.setDriverAllowance(c.getDriverAllowance().doubleValue());
                }
                if (c.getDisplayOrder() != null) {
                    vt.setPriority(c.getDisplayOrder());
                }
                vt.setStatus(Boolean.FALSE.equals(c.getActive()) ? "inactive" : "active");
                vt.setServiceType("PASSENGER");
            }

            return vehicleTypeRepository.save(vt);
        } catch (Exception e) {
            log.warn("Failed to sync PassengerVehicleCategory {}: {}", c != null ? c.getCategoryCode() : "null", e.getMessage());
            return null;
        }
    }

    /**
     * When a PassengerVehicleCategory is deactivated or deleted, update VehicleType.
     */
    public void deletePassengerCategory(PassengerVehicleCategory c) {
        if (c == null || vehicleTypeRepository == null) return;
        try {
            String code = c.getCategoryCode() != null ? c.getCategoryCode().trim().toUpperCase() : "";
            if (code.isBlank()) return;

            vehicleTypeRepository.findById(code).ifPresent(vt -> {
                vt.setStatus("inactive");
                vehicleTypeRepository.save(vt);
            });
        } catch (Exception e) {
            log.warn("Failed to deactivate VehicleType on PassengerVehicleCategory delete: {}", e.getMessage());
        }
    }

    private String resolveDeliveryIcon(String category, String name) {
        String c = (category != null ? category : "").toLowerCase();
        String n = (name != null ? name : "").toLowerCase();
        if (c.contains("bike") || c.contains("scooter") || c.contains("two_wheel") || n.contains("bike") || n.contains("2-wheel") || n.contains("2 wheel")) {
            return "bike";
        }
        if (c.contains("auto") || c.contains("rickshaw") || n.contains("auto")) {
            return "rickshaw";
        }
        if (c.contains("pack") || n.contains("pack")) {
            return "package-variant";
        }
        return "truck-delivery";
    }

    private String resolvePassengerIcon(String code) {
        String c = (code != null ? code : "").toUpperCase();
        if (c.contains("BIKE") || c.contains("MOTO")) return "bike";
        if (c.contains("AUTO")) return "rickshaw";
        return "car";
    }

    private String resolveLegacyDeliveryId(String svcId) {
        if (svcId == null) return null;
        String s = svcId.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (s.equals("pickup8ft") || s.equals("pickup")) return "veh_pickup_04";
        if (s.equals("mini3w") || s.equals("minitruck")) return "veh_minitruck_05";
        if (s.equals("tataace")) return "veh_tataace_06";
        if (s.equals("scooter")) return "veh_scooter_02";
        if (s.equals("14ft") || s.equals("407truck") || s.equals("407")) return "veh_407_07";
        if (s.equals("17ft") || s.equals("lpt1109") || s.equals("1109")) return "veh_lpt1109_08";
        return null;
    }

    private String resolveLegacyPassengerId(String code) {
        if (code == null) return null;
        String s = code.toUpperCase();
        if (s.contains("BIKE")) return "pass_bike";
        if (s.contains("AUTO")) return "pass_auto";
        if (s.contains("CAB") || s.contains("SEDAN") || s.contains("HATCHBACK")) return "6";
        return null;
    }

    public static boolean isNonVehicleArtifact(String name, String id) {
        if (name == null && id == null) return false;
        String n = (name != null ? name : "").toLowerCase().trim();
        String i = (id != null ? id : "").toLowerCase().trim();
        return n.equals("one-way ride") || n.equals("round trip") || n.equals("local rental")
                || n.equals("airport transfer") || n.equals("porter trucks & fleet")
                || n.equals("2 wheeler / bike") || n.equals("scooter model")
                || n.equals("scooty") || n.equals("vehicle")
                || i.equals("one_way") || i.equals("round_trip") || i.equals("rental")
                || i.equals("airport_transfer") || i.equals("porter-trucks-fleet")
                || i.equals("2-wheeler-bike") || i.equals("one-way") || i.equals("round-trip")
                || i.equals("local-rental") || i.equals("airport-transfer");
    }
}
