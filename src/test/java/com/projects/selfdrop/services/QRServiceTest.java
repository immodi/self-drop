package com.projects.selfdrop.services;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class QRServiceTest {

    @Test
    void generatesAPngWithAWhiteQuietZone() throws Exception {
        String encoded = new QRService()
                .generate("selfdrop://pair?u=http%3A%2F%2F192.168.1.50%3A8080&id=abc123");

        byte[] png = Base64.getDecoder().decode(encoded);
        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(image.getHeight());
        assertThat(image.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void honoursTheRequestedScale() throws Exception {
        QRService service = new QRService();

        BufferedImage small = image(service.generate("hello", 2));
        BufferedImage large = image(service.generate("hello", 8));

        assertThat(large.getWidth()).isGreaterThan(small.getWidth());
    }

    private static BufferedImage image(String encoded) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
    }
}
