package com.anushaporter.backend;

import com.anushaporter.backend.dto.ValidationReason;
import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;
import com.anushaporter.backend.service.document.FaceDetectionService;
import com.anushaporter.backend.service.document.OcrExtractionService;
import com.anushaporter.backend.service.document.QrCodeService;
import com.anushaporter.backend.service.document.validators.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentValidationUnitTest {

    private OcrExtractionService ocrService;
    private QrCodeService qrCodeService;
    private FaceDetectionService faceDetectionService;

    private AadhaarValidator aadhaarValidator;
    private PanValidator panValidator;
    private DrivingLicenceValidator drivingLicenceValidator;
    private RcValidator rcValidator;
    private BankDocumentValidator bankDocumentValidator;
    private FaceValidator faceValidator;

    @BeforeEach
    void setUp() {
        ocrService = new OcrExtractionService();
        qrCodeService = new QrCodeService();
        faceDetectionService = new FaceDetectionService();
        faceDetectionService.init();

        aadhaarValidator = new AadhaarValidator(ocrService, qrCodeService);
        panValidator = new PanValidator(ocrService);
        drivingLicenceValidator = new DrivingLicenceValidator(ocrService);
        rcValidator = new RcValidator(ocrService);
        bankDocumentValidator = new BankDocumentValidator(ocrService);
        faceValidator = new FaceValidator(faceDetectionService);
    }

    private byte[] createImageWithText(String... lines) throws IOException {
        BufferedImage image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 1000, 600);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 28));

        int y = 60;
        for (String line : lines) {
            g.drawString(line, 50, y);
            y += 45;
        }
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private byte[] createRandomPhoto() throws IOException {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 600, 400);
        g.setColor(Color.CYAN);
        g.fillOval(50, 50, 300, 200);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private byte[] createFaceImage() throws IOException {
        BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 41, 59));
        g.fillRect(0, 0, 600, 600);

        // Skin tone face oval
        g.setColor(new Color(250, 200, 180));
        g.fillOval(180, 120, 240, 320);

        // Eyes
        g.setColor(Color.BLACK);
        g.fillOval(230, 220, 25, 25);
        g.fillOval(345, 220, 25, 25);

        // Mouth
        g.drawArc(260, 320, 80, 40, 0, -180);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private byte[] createPitchBlackImage() throws IOException {
        BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 400, 400);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void testAadhaarValidator_ValidAadhaar() throws Exception {
        byte[] aadhaarImage = createImageWithText(
                "GOVERNMENT OF INDIA",
                "UNIQUE IDENTIFICATION AUTHORITY OF INDIA",
                "Name: Rajesh Sharma",
                "DOB: 12/04/1992",
                "Gender: MALE",
                "5489 3214 9081"
        );

        ValidationResult result = aadhaarValidator.validate(aadhaarImage);
        assertTrue(result.isValid());
        assertEquals(200, result.getStatus());
        assertEquals(DocumentType.AADHAAR, result.getDocumentType());
    }

    @Test
    void testAadhaarValidator_Mismatch_PanSubmitted() throws Exception {
        byte[] panImage = createImageWithText(
                "INCOME TAX DEPARTMENT",
                "GOVT. OF INDIA",
                "PERMANENT ACCOUNT NUMBER",
                "ABCDE1234F"
        );

        ValidationResult result = aadhaarValidator.validate(panImage);
        assertFalse(result.isValid());
        assertEquals(422, result.getStatus());
        assertEquals(ValidationReason.DOCUMENT_TYPE_MISMATCH, result.getReason());
        assertTrue(result.getMessage().contains("Aadhaar"));
    }

    @Test
    void testPanValidator_ValidPan() throws Exception {
        byte[] panImage = createImageWithText(
                "INCOME TAX DEPARTMENT",
                "GOVT. OF INDIA",
                "PERMANENT ACCOUNT NUMBER",
                "Name: SURESH VERMA",
                "Father: RAMESH VERMA",
                "ABCDE1234F"
        );

        ValidationResult result = panValidator.validate(panImage);
        assertTrue(result.isValid());
        assertEquals(200, result.getStatus());
        assertEquals(DocumentType.PAN, result.getDocumentType());
    }

    @Test
    void testPanValidator_Mismatch_RcSubmitted() throws Exception {
        byte[] rcImage = createImageWithText(
                "CERTIFICATE OF REGISTRATION",
                "FORM 23",
                "REGISTERING AUTHORITY",
                "Regn No: MH12AB1234",
                "Chassis No: MA3EWB2S880012345",
                "Engine No: K12MN8823145"
        );

        ValidationResult result = panValidator.validate(rcImage);
        assertFalse(result.isValid());
        assertEquals(422, result.getStatus());
        assertEquals(ValidationReason.DOCUMENT_TYPE_MISMATCH, result.getReason());
        assertTrue(result.getMessage().contains("PAN"));
    }

    @Test
    void testDrivingLicenceValidator_ValidDL() throws Exception {
        byte[] dlImage = createImageWithText(
                "INDIAN UNION DRIVING LICENCE",
                "TRANSPORT DEPARTMENT",
                "Licence No: MH12 20180012345",
                "Authorisation: LMV, MCWG",
                "Valid Till: 20/05/2038"
        );

        ValidationResult result = drivingLicenceValidator.validate(dlImage);
        assertTrue(result.isValid());
        assertEquals(200, result.getStatus());
        assertEquals(DocumentType.DRIVING_LICENCE, result.getDocumentType());
    }

    @Test
    void testDrivingLicenceValidator_Mismatch_BankPassbookSubmitted() throws Exception {
        byte[] bankImage = createImageWithText(
                "STATE BANK OF INDIA",
                "PASSBOOK / STATEMENT",
                "Account No: 30894567123",
                "IFSC Code: SBIN0001234",
                "Branch: KORAMANGALA"
        );

        ValidationResult result = drivingLicenceValidator.validate(bankImage);
        assertFalse(result.isValid());
        assertEquals(422, result.getStatus());
        assertEquals(ValidationReason.DOCUMENT_TYPE_MISMATCH, result.getReason());
        assertTrue(result.getMessage().contains("Driving Licence"));
    }

    @Test
    void testRcValidator_ValidRc() throws Exception {
        byte[] rcImage = createImageWithText(
                "CERTIFICATE OF REGISTRATION",
                "FORM 23",
                "REGISTERING AUTHORITY",
                "Regn No: MH12AB1234",
                "Chassis No: MA3EWB2S880012345",
                "Engine No: K12MN8823145",
                "Vehicle Class: LMV"
        );

        ValidationResult result = rcValidator.validate(rcImage);
        assertTrue(result.isValid());
        assertEquals(200, result.getStatus());
        assertEquals(DocumentType.RC, result.getDocumentType());
    }

    @Test
    void testBankDocumentValidator_ValidBankDoc() throws Exception {
        byte[] bankImage = createImageWithText(
                "HDFC BANK",
                "ACCOUNT STATEMENT / PASSBOOK",
                "Account No: 50100234567890",
                "IFSC: HDFC0000123",
                "Branch: INDIRANAGAR"
        );

        ValidationResult result = bankDocumentValidator.validate(bankImage);
        assertTrue(result.isValid());
        assertEquals(200, result.getStatus());
        assertEquals(DocumentType.BANK_DOCUMENT, result.getDocumentType());
    }

    @Test
    void testFaceValidator_DarkImage_Rejected() throws Exception {
        byte[] darkImage = createPitchBlackImage();
        ValidationResult result = faceValidator.validate(darkImage);
        assertFalse(result.isValid());
        assertEquals(422, result.getStatus());
        assertEquals(ValidationReason.IMAGE_TOO_DARK, result.getReason());
    }

    @Test
    void testFaceValidator_ValidFace() throws Exception {
        byte[] faceImage = createFaceImage();
        ValidationResult result = faceValidator.validate(faceImage);
        assertTrue(result.isValid());
        assertEquals(200, result.getStatus());
        assertEquals(DocumentType.FACE, result.getDocumentType());
    }

    @Test
    void testRandomImageRejectedAcrossAllDocTypes() throws Exception {
        byte[] randomImage = createRandomPhoto();

        ValidationResult r1 = aadhaarValidator.validate(randomImage);
        assertFalse(r1.isValid());

        ValidationResult r2 = panValidator.validate(randomImage);
        assertFalse(r2.isValid());

        ValidationResult r3 = drivingLicenceValidator.validate(randomImage);
        assertFalse(r3.isValid());

        ValidationResult r4 = rcValidator.validate(randomImage);
        assertFalse(r4.isValid());

        ValidationResult r5 = bankDocumentValidator.validate(randomImage);
        assertFalse(r5.isValid());
    }
}
