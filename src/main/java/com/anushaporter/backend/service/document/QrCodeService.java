package com.anushaporter.backend.service.document;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

@Service
public class QrCodeService {

    private static final Logger log = LoggerFactory.getLogger(QrCodeService.class);

    /**
     * Attempts to detect and decode a QR code from raw image bytes.
     *
     * @param imageBytes image data
     * @return Decoded string text, or null if no QR code was found
     */
    public String decodeQrCode(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                return null;
            }
            return decodeQrCode(bufferedImage);
        } catch (Exception e) {
            log.debug("QR decoding exception: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to detect and decode a QR code from a BufferedImage.
     */
    public String decodeQrCode(BufferedImage image) {
        if (image == null) {
            return null;
        }

        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            Result result = new MultiFormatReader().decode(bitmap, hints);
            if (result != null && result.getText() != null && !result.getText().isBlank()) {
                log.info("✓ Detected and decoded QR code: length={}", result.getText().length());
                return result.getText();
            }
        } catch (NotFoundException e) {
            // QR code not found in image - expected for non-QR images
            log.debug("No QR code found in image.");
        } catch (Exception e) {
            log.debug("Error during QR code scan: {}", e.getMessage());
        }

        return null;
    }
}
