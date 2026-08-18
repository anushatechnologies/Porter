package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "drivers")
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name; 
    private String phone; 
    private String vehicle; 
    private String rating; 
    private String status; 
    private String location;
    private Double latitude;
    private Double longitude;
    private Double heading;
    private Double speed; // speed in km/h (0.0 when parked)

    @Column(unique = true)
    private String email;
    
    private String dob;
    private String gender;
    private String vehicleType;
    private String vehicleNumber;
    private String rcNumber;
    private String aadhaarNumber;
    private String licenseNumber;

    private String addressLine1;
    private String city;
    private String state;
    private String pincode;

    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;

    private String profilePhotoUri;
    private String aadhaarUri;
    private String licenseUri;
    private String rcUri;
    private String bankPassbookUri;

    private String kyc;
    private String rejectedReason;
    private Integer trips;
    private String tenure;

    @Column(name = "wallet_balance")
    private Double walletBalance = 0.0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getVehicle() { return vehicle; }
    public void setVehicle(String vehicle) { this.vehicle = vehicle; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public String getRcNumber() { return rcNumber; }
    public void setRcNumber(String rcNumber) { this.rcNumber = rcNumber; }
    public String getAadhaarNumber() { return aadhaarNumber; }
    public void setAadhaarNumber(String aadhaarNumber) { this.aadhaarNumber = aadhaarNumber; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfscCode() { return ifscCode; }
    public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }
    public String getProfilePhotoUri() { return profilePhotoUri; }
    public void setProfilePhotoUri(String profilePhotoUri) { this.profilePhotoUri = profilePhotoUri; }
    public String getAadhaarUri() { return aadhaarUri; }
    public void setAadhaarUri(String aadhaarUri) { this.aadhaarUri = aadhaarUri; }
    public String getLicenseUri() { return licenseUri; }
    public void setLicenseUri(String licenseUri) { this.licenseUri = licenseUri; }
    public String getRcUri() { return rcUri; }
    public void setRcUri(String rcUri) { this.rcUri = rcUri; }
    public String getBankPassbookUri() { return bankPassbookUri; }
    public void setBankPassbookUri(String bankPassbookUri) { this.bankPassbookUri = bankPassbookUri; }
    public String getKyc() { return kyc; }
    public void setKyc(String kyc) { this.kyc = kyc; }
    public String getRejectedReason() { return rejectedReason; }
    public void setRejectedReason(String rejectedReason) { this.rejectedReason = rejectedReason; }
    public Integer getTrips() { return trips; }
    public void setTrips(Integer trips) { this.trips = trips; }
    public String getTenure() { return tenure; }
    public void setTenure(String tenure) { this.tenure = tenure; }

    public Double getWalletBalance() {
        return walletBalance != null ? walletBalance : 0.0;
    }
    public void setWalletBalance(Double walletBalance) {
        this.walletBalance = walletBalance != null ? walletBalance : 0.0;
    }
}
