# Frontend Handoff: API Response Interceptor & Driver Registration Overhaul

This document contains the exact implementation guidelines and code for the Frontend team (React Native / Expo / React Web).

---

## 1. Centralized API Interceptor (`src/utils/apiInterceptor.ts`)

Intercepts HTTP status codes and network errors to display user-friendly Alert dialogs with **[ Try Again ]** buttons without exposing raw technical strings.

```typescript
import axios, { AxiosError, AxiosResponse } from 'axios';
import { Alert } from 'react-native';

export const handleApiError = (error: AxiosError<any>, onRetry?: () => void) => {
  let userMessage = 'Unable to complete registration right now. Please try again later.';

  if (!error.response) {
    // Network Error or Request Timeout
    userMessage = 'No internet connection. Please check your connection and try again.';
  } else {
    const status = error.response.status;
    const data = error.response.data;

    switch (status) {
      case 400:
      case 422:
        userMessage = data?.message || data?.error || 'Please check your details and try again.';
        break;

      case 401:
        userMessage = 'Your session has expired. Please login again.';
        break;

      case 409:
        userMessage = 'Your KYC application already exists.';
        break;

      case 500:
      default:
        // Mask raw "Internal Server Error"
        userMessage = 'Unable to complete registration right now. Please try again later.';
        break;
    }
  }

  Alert.alert(
    'Registration Error',
    userMessage,
    onRetry
      ? [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Try Again', onPress: onRetry },
        ]
      : [{ text: 'OK' }]
  );
};
```

---

## 2. Centralized Input Validators (`src/utils/validators.ts`)

Provides strict regex and length constraints for Indian mobile numbers, names, Aadhaar, PAN, DL, RC, Bank Account, and IFSC.

```typescript
export interface ValidationResult {
  isValid: boolean;
  message?: string;
}

export const validateName = (name: string): ValidationResult => {
  if (!name || name.trim().length < 2) {
    return { isValid: false, message: 'Name must be at least 2 characters long' };
  }
  if (!/^[a-zA-Z\s]{2,50}$/.test(name.trim())) {
    return { isValid: false, message: 'Name must contain only alphabets and spaces' };
  }
  return { isValid: true };
};

export const validatePhone = (phone: string): ValidationResult => {
  const cleanPhone = phone.replace(/\D/g, '');
  if (!/^[6-9]\d{9}$/.test(cleanPhone)) {
    return { isValid: false, message: 'Enter a valid 10-digit Indian mobile number starting with 6-9' };
  }
  return { isValid: true };
};

export const validateAadhaar = (aadhaar: string): ValidationResult => {
  const clean = aadhaar.replace(/\s+/g, '');
  if (!/^\d{12}$/.test(clean)) {
    return { isValid: false, message: 'Aadhaar must contain exactly 12 numeric digits' };
  }
  return { isValid: true };
};

export const validatePan = (pan: string): ValidationResult => {
  const clean = pan.trim().toUpperCase();
  if (!/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/.test(clean)) {
    return { isValid: false, message: 'Invalid PAN card format (e.g. ABCDE1234F)' };
  }
  return { isValid: true };
};

export const validateDrivingLicense = (dl: string): ValidationResult => {
  const clean = dl.trim();
  if (!clean) {
    return { isValid: false, message: 'Driving licence number is required' };
  }
  if (clean.length > 100 || !/^[a-zA-Z0-9]{1,100}$/.test(clean)) {
    return { isValid: false, message: 'Driving licence must contain only numbers and alphabets (up to 100 characters)' };
  }
  return { isValid: true };
};

export const validateRC = (rc: string): ValidationResult => {
  const clean = rc.trim().toUpperCase().replace(/[\s-]/g, '');
  if (!/^[A-Z]{2}\d{2}[A-Z]{1,3}\d{4}$/.test(clean)) {
    return { isValid: false, message: 'Invalid Vehicle RC format (e.g. KA01AB1234)' };
  }
  return { isValid: true };
};

export const validateBankAccount = (account: string): ValidationResult => {
  const clean = account.trim();
  if (!/^\d{9,18}$/.test(clean)) {
    return { isValid: false, message: 'Account number must contain 9 to 18 digits' };
  }
  return { isValid: true };
};

export const validateIFSC = (ifsc: string): ValidationResult => {
  const clean = ifsc.trim().toUpperCase();
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(clean)) {
    return { isValid: false, message: 'Invalid IFSC format (e.g. HDFC0001234)' };
  }
  return { isValid: true };
};
```

---

## 3. UI Implementation & Form Handling (`DriverRegistrationScreen.tsx`)

### Key Highlights:
1. **Dynamic UI States**: Input borders turn red (`#DC2626`) and display error text when validation fails.
2. **Save & Next Per Step**: Each registration step features a **[ Save and Next ]** button that immediately persists step data to the backend database via `POST /api/drivers/register` with `saveAndNext: true`.
3. **Form Data Preservation & Resume ("Again Registration")**: When a driver returns to registration, `GET /api/drivers/register/progress` restores all previously saved fields from the database and jumps directly to their saved step, preventing duplicate entries or 409 Conflict errors.
4. **Non-destructive Backend Updates**: Only fields provided in each step are updated; earlier step fields are never overwritten or wiped.
5. **Rapid Tapping Guard**: `isSubmitting` flag and submission guard prevent duplicate API invocations.

```typescript
import React, { useState, useEffect, useRef } from 'react';
import { View, Text, TextInput, TouchableOpacity, ActivityIndicator, Alert, ScrollView } from 'react-native';
import { handleApiError } from '../utils/apiInterceptor';
import {
  validateName,
  validatePhone,
  validateAadhaar,
  validateDrivingLicense,
  validateRC,
  validateBankAccount,
  validateIFSC,
} from '../utils/validators';
import axios from 'axios';

export const DriverRegistrationScreen = ({ navigation }: any) => {
  const [currentStep, setCurrentStep] = useState<number>(1);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isLoadingDraft, setIsLoadingDraft] = useState<boolean>(true);
  const isSubmittingRef = useRef<boolean>(false);

  // Form State (Preserved in Database on each step)
  const [formData, setFormData] = useState({
    name: '',
    phone: '',
    email: '',
    dob: '1995-01-01',
    gender: 'Male',
    vehicleType: 'Bike',
    vehicleNumber: '',
    rcNumber: '',
    licenseNumber: '',
    aadhaarNumber: '',
    addressLine1: '',
    city: '',
    state: '',
    pincode: '',
    bankName: '',
    accountHolderName: '',
    accountNumber: '',
    ifscCode: '',
  });

  const [documents, setDocuments] = useState({
    profilePhotoUrl: '',
    aadhaarUrl: '',
    licenseUrl: '',
    rcUrl: '',
    bankPassbookUrl: '',
  });

  // Validation Error States
  const [errors, setErrors] = useState<Record<string, string>>({});

  // 1. Restore previously saved data on mount ("again registration")
  useEffect(() => {
    const loadSavedProgress = async () => {
      try {
        const res = await axios.get('/api/drivers/register/progress');
        if (res.data?.hasDraft) {
          setFormData((prev) => ({
            ...prev,
            name: res.data.name || prev.name,
            phone: res.data.phone || prev.phone,
            dob: res.data.dob || prev.dob,
            gender: res.data.gender || prev.gender,
            vehicleType: res.data.vehicleType || prev.vehicleType,
            vehicleNumber: res.data.vehicleNumber || prev.vehicleNumber,
            rcNumber: res.data.rcNumber || prev.rcNumber,
            licenseNumber: res.data.licenseNumber || prev.licenseNumber,
            aadhaarNumber: res.data.aadhaarNumber || prev.aadhaarNumber,
            addressLine1: res.data.addressLine1 || prev.addressLine1,
            city: res.data.city || prev.city,
            state: res.data.state || prev.state,
            pincode: res.data.pincode || prev.pincode,
            bankName: res.data.bankName || prev.bankName,
            accountHolderName: res.data.accountHolderName || prev.accountHolderName,
            accountNumber: res.data.accountNumber || prev.accountNumber,
            ifscCode: res.data.ifscCode || prev.ifscCode,
          }));
          if (res.data.registrationStep && res.data.registrationStep > 1) {
            setCurrentStep(res.data.registrationStep);
          }
        }
      } catch (err) {
        console.warn('Could not load existing registration draft:', err);
      } finally {
        setIsLoadingDraft(false);
      }
    };
    loadSavedProgress();
  }, []);

  const updateField = (key: string, value: string) => {
    setFormData((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((prev) => ({ ...prev, [key]: '' }));
    }
  };

  // Step 1 Validation
  const validateStep1 = (): boolean => {
    const newErrors: Record<string, string> = {};
    const nameRes = validateName(formData.name);
    if (!nameRes.isValid) newErrors.name = nameRes.message!;
    const phoneRes = validatePhone(formData.phone);
    if (!phoneRes.isValid) newErrors.phone = phoneRes.message!;
    setErrors((prev) => ({ ...prev, ...newErrors }));
    return Object.keys(newErrors).length === 0;
  };

  // Step 2 Validation (Vehicles & DL)
  const validateStep2 = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (formData.licenseNumber) {
      const dlRes = validateDrivingLicense(formData.licenseNumber);
      if (!dlRes.isValid) newErrors.licenseNumber = dlRes.message!;
    }
    setErrors((prev) => ({ ...prev, ...newErrors }));
    return Object.keys(newErrors).length === 0;
  };

  // Save and Next: Persists current step to DB and advances step
  const handleSaveAndNext = async (stepNum: number) => {
    if (isSubmittingRef.current || isSubmitting) return;

    if (stepNum === 1 && !validateStep1()) return;
    if (stepNum === 2 && !validateStep2()) return;

    setIsSubmitting(true);
    isSubmittingRef.current = true;

    try {
      const response = await axios.post('/api/drivers/register', {
        ...formData,
        documents,
        step: stepNum,
        saveAndNext: true,
      });

      if (response.data?.success) {
        const nextStep = response.data.nextStep || stepNum + 1;
        setCurrentStep(nextStep);
      }
    } catch (error: any) {
      handleApiError(error, () => handleSaveAndNext(stepNum));
    } finally {
      setIsSubmitting(false);
      isSubmittingRef.current = false;
    }
  };

  // Final Submit
  const handleSubmit = async () => {
    if (isSubmittingRef.current || isSubmitting) return;

    setIsSubmitting(true);
    isSubmittingRef.current = true;

    try {
      const response = await axios.post('/api/drivers/register', {
        ...formData,
        documents,
        submit: true,
      });

      if (response.data?.success) {
        Alert.alert('Success', 'Your driver registration KYC has been submitted for approval!');
        navigation.navigate('DriverDashboard');
      }
    } catch (error: any) {
      handleApiError(error, handleSubmit);
    } finally {
      setIsSubmitting(false);
      isSubmittingRef.current = false;
    }
  };

  if (isLoadingDraft) {
    return (
      <View className="flex-1 justify-center items-center bg-white">
        <ActivityIndicator size="large" color="#2563EB" />
      </View>
    );
  }

  return (
    <ScrollView className="flex-1 bg-white p-4">
      {currentStep === 1 && (
        <View className="space-y-4">
          <Text className="text-xl font-bold">Step 1: Personal Details</Text>

          {/* Profile Photo Picker */}
          <View className="items-center mb-4">
            <Text className="text-sm font-medium mb-2">Profile Photo *</Text>
            <TouchableOpacity
              onPress={() => {/* Launch Image Picker and upload to /api/documents/upload or /api/drivers/upload */}}
              style={{
                width: 96,
                height: 96,
                borderRadius: 48,
                backgroundColor: '#F3F4F6',
                borderWidth: 2,
                borderColor: errors.profilePhoto ? '#DC2626' : '#2563EB',
                justifyContent: 'center',
                alignItems: 'center',
                overflow: 'hidden',
              }}
            >
              {documents.profilePhotoUrl ? (
                <Text style={{ fontSize: 12, color: '#16A34A', fontWeight: 'bold' }}>Photo Added</Text>
              ) : (
                <Text style={{ fontSize: 12, color: '#6B7280', textAlign: 'center' }}>+ Upload Photo</Text>
              )}
            </TouchableOpacity>
            {!!errors.profilePhoto && <Text style={{ color: '#DC2626', fontSize: 12, marginTop: 4 }}>{errors.profilePhoto}</Text>}
          </View>

          {/* Name Field */}
          <View>
            <Text className="text-sm font-medium mb-1">Full Name *</Text>
            <TextInput
              value={formData.name}
              onChangeText={(text) => updateField('name', text)}
              placeholder="Enter full name"
              style={{
                borderWidth: 1,
                borderColor: errors.name ? '#DC2626' : '#D1D5DB',
                borderRadius: 8,
                padding: 12,
              }}
            />
            {!!errors.name && <Text style={{ color: '#DC2626', fontSize: 12, marginTop: 4 }}>{errors.name}</Text>}
          </View>

          {/* Phone Field */}
          <View>
            <Text className="text-sm font-medium mb-1">Phone Number *</Text>
            <TextInput
              value={formData.phone}
              onChangeText={(text) => updateField('phone', text)}
              keyboardType="phone-pad"
              placeholder="Enter 10-digit mobile number"
              style={{
                borderWidth: 1,
                borderColor: errors.phone ? '#DC2626' : '#D1D5DB',
                borderRadius: 8,
                padding: 12,
              }}
            />
            {!!errors.phone && <Text style={{ color: '#DC2626', fontSize: 12, marginTop: 4 }}>{errors.phone}</Text>}
          </View>

          {/* Email Field */}
          <View>
            <Text className="text-sm font-medium mb-1">Email Address *</Text>
            <TextInput
              value={formData.email}
              onChangeText={(text) => updateField('email', text)}
              keyboardType="email-address"
              autoCapitalize="none"
              placeholder="name@example.com"
              style={{
                borderWidth: 1,
                borderColor: errors.email ? '#DC2626' : '#D1D5DB',
                borderRadius: 8,
                padding: 12,
              }}
            />
            {!!errors.email && <Text style={{ color: '#DC2626', fontSize: 12, marginTop: 4 }}>{errors.email}</Text>}
          </View>

          {/* Date of Birth Field */}
          <View>
            <Text className="text-sm font-medium mb-1">Date of Birth (YYYY-MM-DD) *</Text>
            <TextInput
              value={formData.dob}
              onChangeText={(text) => updateField('dob', text)}
              placeholder="1995-01-01"
              style={{
                borderWidth: 1,
                borderColor: errors.dob ? '#DC2626' : '#D1D5DB',
                borderRadius: 8,
                padding: 12,
              }}
            />
            {!!errors.dob && <Text style={{ color: '#DC2626', fontSize: 12, marginTop: 4 }}>{errors.dob}</Text>}
          </View>

          {/* Gender Selector */}
          <View>
            <Text className="text-sm font-medium mb-1">Gender *</Text>
            <View style={{ flexDirection: 'row', gap: 12, marginTop: 4 }}>
              {['Male', 'Female', 'Other'].map((g) => (
                <TouchableOpacity
                  key={g}
                  onPress={() => updateField('gender', g)}
                  style={{
                    flex: 1,
                    paddingVertical: 10,
                    borderRadius: 8,
                    borderWidth: 1,
                    borderColor: formData.gender === g ? '#2563EB' : '#D1D5DB',
                    backgroundColor: formData.gender === g ? '#EFF6FF' : '#FFFFFF',
                    alignItems: 'center',
                  }}
                >
                  <Text style={{ color: formData.gender === g ? '#2563EB' : '#374151', fontWeight: '600' }}>{g}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          {/* Save and Next Button */}
          <TouchableOpacity
            onPress={() => handleSaveAndNext(1)}
            disabled={isSubmitting}
            style={{
              backgroundColor: isSubmitting ? '#9CA3AF' : '#2563EB',
              padding: 16,
              borderRadius: 8,
              alignItems: 'center',
              marginTop: 24,
            }}
          >
            {isSubmitting ? <ActivityIndicator color="#FFFFFF" /> : <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Save and Next</Text>}
          </TouchableOpacity>
        </View>
      )}

      {currentStep === 2 && (
        <View className="space-y-4">
          <Text className="text-xl font-bold">Step 2: Vehicle & Driving Licence</Text>

          {/* Driving Licence Field (Numbers and Alphabets up to 100) */}
          <View>
            <Text className="text-sm font-medium mb-1">Driving Licence Number (Alphanumeric, max 100)</Text>
            <TextInput
              value={formData.licenseNumber}
              onChangeText={(text) => updateField('licenseNumber', text)}
              autoCapitalize="characters"
              maxLength={100}
              placeholder="e.g. MH1220230001234"
              style={{
                borderWidth: 1,
                borderColor: errors.licenseNumber ? '#DC2626' : '#D1D5DB',
                borderRadius: 8,
                padding: 12,
              }}
            />
            {!!errors.licenseNumber && <Text style={{ color: '#DC2626', fontSize: 12, marginTop: 4 }}>{errors.licenseNumber}</Text>}
          </View>

          {/* Save and Next Button */}
          <TouchableOpacity
            onPress={() => handleSaveAndNext(2)}
            disabled={isSubmitting}
            style={{
              backgroundColor: isSubmitting ? '#9CA3AF' : '#2563EB',
              padding: 16,
              borderRadius: 8,
              alignItems: 'center',
              marginTop: 24,
            }}
          >
            {isSubmitting ? <ActivityIndicator color="#FFFFFF" /> : <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Save and Next</Text>}
          </TouchableOpacity>
        </View>
      )}

      {currentStep === 3 && (
        <View className="space-y-4">
          <Text className="text-xl font-bold">Step 3: Review & Submit</Text>

          {/* Submit Button */}
          <TouchableOpacity
            onPress={handleSubmit}
            disabled={isSubmitting}
            style={{
              backgroundColor: isSubmitting ? '#9CA3AF' : '#16A34A',
              padding: 16,
              borderRadius: 8,
              alignItems: 'center',
              marginTop: 24,
            }}
          >
            {isSubmitting ? <ActivityIndicator color="#FFFFFF" /> : <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Submit Application</Text>}
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
};
```

---

## 4. Customer App: Order Cancellation Window (`CustomerLiveTrackingScreen.tsx`)

### Cancellation Window Rule:
- **ACTIVE (`canCancel: true`)**: While placing the order (`searching`, `pending`, `created`), driver assignment (`assigned`), driver en route to pickup, arrived at pickup, and in transit until the driver is close to the drop location.
- **BLOCKED (`canCancel: false`)**: Once the driver arrives near the drop location (`driver_reached`, `arrived_at_drop`, `unloading`, or GPS distance <= 300 meters from drop coordinates), delivery OTP verified, or order completed/delivered.

### Backend Endpoints:
- `GET /api/bookings/{bookingId}/tracking`:
  ```json
  {
    "canCancel": true,
    "isCancellable": true,
    "cancellationBlockedReason": null,
    "cancellationMessage": "Cancellation is available until driver arrives at drop location."
  }
  ```
- `PUT /api/bookings/{bookingId}/cancel` (or `POST /api/bookings/{bookingId}/cancel`):
  - Request body: `{ "reason": "Customer requested cancellation" }`
  - Returns: `{ "success": true, "status": "cancelled", "message": "Booking cancelled successfully" }`
  - Note: Whitelisted in `AuthInterceptor` so cancellation never fails due to stale/missing auth headers.

### React Native Cancellation Button Component:
```tsx
import React, { useState } from 'react';
import { View, Text, TouchableOpacity, Alert, Modal, TextInput } from 'react-native';
import axios from 'axios';

interface Props {
  bookingId: string;
  trackingData: {
    canCancel: boolean;
    cancellationBlockedReason?: string;
  };
  onCancelled: () => void;
}

export const OrderCancellationSection = ({ bookingId, trackingData, onCancelled }: Props) => {
  const [modalVisible, setModalVisible] = useState(false);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);

  const handleCancelOrder = async () => {
    setLoading(true);
    try {
      const res = await axios.put(`/api/bookings/${bookingId}/cancel`, {
        reason: reason || 'Customer requested cancellation',
      });
      if (res.data?.success) {
        Alert.alert('Order Cancelled', 'Your order has been cancelled successfully.');
        setModalVisible(false);
        onCancelled();
      }
    } catch (err: any) {
      const errMsg = err.response?.data?.message || 'Cancellation window has expired.';
      Alert.alert('Unable to Cancel', errMsg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={{ padding: 16, backgroundColor: '#FFFFFF', borderTopWidth: 1, borderColor: '#F3F4F6' }}>
      {trackingData.canCancel ? (
        <TouchableOpacity
          onPress={() => setModalVisible(true)}
          style={{
            backgroundColor: '#FEE2E2',
            borderColor: '#EF4444',
            borderWidth: 1,
            paddingVertical: 12,
            borderRadius: 8,
            alignItems: 'center',
          }}
        >
          <Text style={{ color: '#DC2626', fontWeight: 'bold' }}>Cancel Order</Text>
        </TouchableOpacity>
      ) : (
        <View style={{ backgroundColor: '#F3F4F6', padding: 12, borderRadius: 8 }}>
          <Text style={{ color: '#6B7280', fontSize: 12, textAlign: 'center' }}>
            {trackingData.cancellationBlockedReason || 'Cancellation is locked as driver has arrived near drop location.'}
          </Text>
        </View>
      )}

      <Modal visible={modalVisible} transparent animationType="slide">
        <View style={{ flex: 1, justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.5)', padding: 20 }}>
          <View style={{ backgroundColor: '#FFFFFF', borderRadius: 12, padding: 20 }}>
            <Text style={{ fontSize: 18, fontWeight: 'bold', marginBottom: 12 }}>Cancel Order</Text>
            <Text style={{ fontSize: 14, color: '#4B5563', marginBottom: 16 }}>
              Are you sure you want to cancel this order?
            </Text>
            <TextInput
              value={reason}
              onChangeText={setReason}
              placeholder="Reason for cancellation (optional)"
              style={{ borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8, padding: 10, marginBottom: 20 }}
            />
            <View style={{ flexDirection: 'row', gap: 12 }}>
              <TouchableOpacity
                onPress={() => setModalVisible(false)}
                style={{ flex: 1, padding: 12, borderRadius: 8, borderWidth: 1, borderColor: '#D1D5DB', alignItems: 'center' }}
              >
                <Text style={{ color: '#374151' }}>Back</Text>
              </TouchableOpacity>
              <TouchableOpacity
                onPress={handleCancelOrder}
                disabled={loading}
                style={{ flex: 1, padding: 12, borderRadius: 8, backgroundColor: '#DC2626', alignItems: 'center' }}
              >
                <Text style={{ color: '#FFFFFF', fontWeight: 'bold' }}>Confirm Cancel</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
};
```

---

## 5. Driver App: Simultaneous Order Broadcast & Stop Ringtone

### Broadcast Architecture:
1. **Simultaneous Multi-Driver Broadcast**: When a customer books, the backend queries all eligible drivers matching the vehicle category within the current radius tier (5km, 10km, etc.) and dispatches incoming offers to all of them at once via push notification and WebSockets.
2. **Instant Ringtone Stop on Acceptance**: The moment ANY driver taps "Accept":
   - Winning driver receives assignment confirmation (`ASSIGNED`).
   - Backend broadcasts a high-priority silent push notification to all other drivers with `action: "STOP_RINGTONE"` and `type: "STOP_DRIVER_OFFER"`.
   - Backend emits a WebSocket event `driver:offer:stop` to the telemetry channel.
   - Competing offers in the database are marked `TOO_LATE`.

### Driver App Notification & Sound Listener:
```typescript
import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import { Audio } from 'av-playback-service'; // or expo-av

let soundObject: Audio.Sound | null = null;

export const useDriverIncomingRideListener = (
  onNewOffer: (offer: any) => void,
  onDismissOffer: (bookingId: string) => void
) => {
  useEffect(() => {
    // 1. Push Notification Listener (FCM / Expo)
    const subscription = Notifications.addNotificationReceivedListener((notification) => {
      const data = notification.request.content.data;

      // When ANY driver accepts the ride, stop ringtone and dismiss modal
      if (data?.action === 'STOP_RINGTONE' || data?.type === 'STOP_DRIVER_OFFER') {
        stopRingtone();
        if (data?.bookingId) {
          onDismissOffer(data.bookingId);
        }
      }

      // When a new ride offer arrives, play ringtone and show offer modal
      if (data?.type === 'DRIVER_OFFER' && data?.action !== 'STOP_RINGTONE') {
        playRingtone();
        onNewOffer(data);
      }
    });

    // 2. Telemetry WebSocket Listener (Realtime Fallback)
    const socket = new WebSocket('wss://api.anushaporter.com/ws/telemetry');
    socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.type === 'driver:offer:stop' || message.action === 'STOP_RINGTONE') {
          stopRingtone();
          if (message.bookingId) {
            onDismissOffer(message.bookingId);
          }
        }
      } catch (err) {
        console.error('Failed to parse websocket message', err);
      }
    };

    return () => {
      subscription.remove();
      socket.close();
      stopRingtone();
    };
  }, []);

  const playRingtone = async () => {
    try {
      if (!soundObject) {
        const { sound } = await Audio.Sound.createAsync(
          require('../assets/sounds/order_ringtone.mp3'),
          { shouldPlay: true, isLooping: true }
        );
        soundObject = sound;
      } else {
        await soundObject.playAsync();
      }
    } catch (e) {
      console.warn('Could not play ringtone', e);
    }
  };

  const stopRingtone = async () => {
    try {
      if (soundObject) {
        await soundObject.stopAsync();
        await soundObject.unloadAsync();
        soundObject = null;
      }
    } catch (e) {
      console.warn('Could not stop ringtone', e);
    }
  };
};
```
