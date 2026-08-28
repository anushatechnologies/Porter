package com.anushaporter.backend.service.document.validators;

import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;
import com.anushaporter.backend.service.document.DocumentValidator;
import com.anushaporter.backend.service.document.FaceDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FaceValidator implements DocumentValidator {

    private final FaceDetectionService faceDetectionService;

    @Autowired
    public FaceValidator(FaceDetectionService faceDetectionService) {
        this.faceDetectionService = faceDetectionService;
    }

    @Override
    public DocumentType supports() {
        return DocumentType.FACE;
    }

    @Override
    public ValidationResult validate(byte[] imageBytes) {
        return faceDetectionService.validateFace(imageBytes);
    }
}
