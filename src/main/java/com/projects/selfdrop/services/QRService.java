package com.projects.selfdrop.services;

import io.nayuki.qrcodegen.QrCode;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
public class QRService {
    private static final int QUIET_ZONE_MODULES = 4;
    private static final int DEFAULT_SCALE = 6;

    /**
     * Encodes {@code data} as a PNG and returns it base64-encoded, ready for a
     * {@code data:image/png;base64,...} source.
     */
    public String generate(String data) throws IOException {
        return generate(data, DEFAULT_SCALE);
    }

    public String generate(String data, int scale) throws IOException {
        if (scale < 1) {
            throw new IllegalArgumentException("Scale must be at least 1");
        }

        QrCode qr = QrCode.encodeText(data, QrCode.Ecc.MEDIUM);

        int size = (qr.size + 2 * QUIET_ZONE_MODULES) * scale;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int moduleX = x / scale - QUIET_ZONE_MODULES;
                int moduleY = y / scale - QUIET_ZONE_MODULES;
                boolean dark = isDarkModule(qr, moduleX, moduleY);
                img.setRGB(x, y, dark ? 0x000000 : 0xFFFFFF);
            }
        }
        return toBase64(img);
    }

    private static boolean isDarkModule(QrCode qr, int x, int y) {
        // The quiet zone is part of the image but not part of the code.
        return x >= 0 && y >= 0 && x < qr.size && y < qr.size && qr.getModule(x, y);
    }

    private static String toBase64(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream container = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", container)) {
                throw new IOException("No ImageIO writer found for PNG");
            }
            return Base64.getEncoder().encodeToString(container.toByteArray());
        }
    }

}
