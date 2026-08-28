package com.anushaporter.backend.dto;

public final class ValidationReason {

    private ValidationReason() {}

    public static final String DOCUMENT_TYPE_MISMATCH = "DOCUMENT_TYPE_MISMATCH";
    public static final String NO_FACE_DETECTED = "NO_FACE_DETECTED";
    public static final String MULTIPLE_FACES_DETECTED = "MULTIPLE_FACES_DETECTED";
    public static final String FACE_TOO_FAR = "FACE_TOO_FAR";
    public static final String IMAGE_TOO_BLURRY = "IMAGE_TOO_BLURRY";
    public static final String IMAGE_TOO_DARK = "IMAGE_TOO_DARK";
    public static final String IMAGE_TOO_BRIGHT = "IMAGE_TOO_BRIGHT";
    public static final String TEXT_NOT_READABLE = "TEXT_NOT_READABLE";
    public static final String INVALID_FILE = "INVALID_FILE";
    public static final String MISSING_REQUIRED_FIELDS = "MISSING_REQUIRED_FIELDS";
    public static final String SUCCESS = "SUCCESS";
}
