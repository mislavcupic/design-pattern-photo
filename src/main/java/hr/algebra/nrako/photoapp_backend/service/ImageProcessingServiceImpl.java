package hr.algebra.nrako.photoapp_backend.service;


import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO; // Korištenje javax.imageio kao što je u tvom sučelju
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
public class ImageProcessingServiceImpl implements ImageProcessingService {

    @Override
    public byte[] processImage(byte[] originalImageBytes, Integer maxWidth, Integer maxHeight, String outputFormat, boolean applySepia, boolean applyBlur) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalImageBytes));
        if (image == null) {
            throw new IOException("Failed to read image bytes into BufferedImage.");
        }

        BufferedImage processedImage = image;

        // 1. Promjena veličine
        if (maxWidth != null && maxHeight != null) {
            processedImage = resizeImage(processedImage, maxWidth, maxHeight);
        } else if (maxWidth != null) {
            // Skaliranje po širini uz zadržavanje omjera
            int newHeight = (int) (processedImage.getHeight() * ((double) maxWidth / processedImage.getWidth()));
            processedImage = resizeImage(processedImage, maxWidth, newHeight);
        } else if (maxHeight != null) {
            // Skaliranje po visini uz zadržavanje omjera
            int newWidth = (int) (processedImage.getWidth() * ((double) maxHeight / processedImage.getHeight()));
            processedImage = resizeImage(processedImage, newWidth, maxHeight);
        }

        // 2. Primjena filtera
        if (applySepia) {
            processedImage = applySepiaFilter(processedImage);
        }
        if (applyBlur) {
            processedImage = applyBlurFilter(processedImage);
        }

        // 3. Konverzija u bajtove željenog formata
        String finalOutputFormat = outputFormat;
        if (finalOutputFormat == null || finalOutputFormat.isEmpty()) {
            finalOutputFormat = getOriginalImageFormat(originalImageBytes);
            if (finalOutputFormat == null) {
                finalOutputFormat = "png"; // Fallback ako se originalni format ne može odrediti
            }
        }

        return toByteArray(processedImage, finalOutputFormat);
    }

    @Override
    public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        // Koristimo Thumbnailator za jednostavnije i kvalitetnije mijenjanje veličine
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Thumbnails.of(originalImage)
                    .size(targetWidth, targetHeight)
                    .toOutputStream(os);
            return ImageIO.read(new ByteArrayInputStream(os.toByteArray()));
        } catch (IOException e) {
            // Logirajte grešku ili bacite prilagođeni runtime exception
            throw new RuntimeException("Failed to resize image using Thumbnailator.", e);
        }
    }

    @Override
    public BufferedImage applySepiaFilter(BufferedImage originalImage) {
        BufferedImage sepiaImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < originalImage.getHeight(); y++) {
            for (int x = 0; x < originalImage.getWidth(); x++) {
                int rgb = originalImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int tr = (int) (0.393 * r + 0.769 * g + 0.189 * b);
                int tg = (int) (0.349 * r + 0.686 * g + 0.168 * b);
                int tb = (int) (0.272 * r + 0.534 * g + 0.131 * b);

                if (tr > 255) tr = 255;
                if (tg > 255) tg = 255;
                if (tb > 255) tb = 255;

                sepiaImage.setRGB(x, y, (tr << 16) | (tg << 8) | tb);
            }
        }
        return sepiaImage;
    }

    @Override
    public BufferedImage applyBlurFilter(BufferedImage originalImage) {
        int radius = 3;
        float weight = 1.0f / (radius * 2 + 1); // Jednostavni box blur
        float[] data = new float[(radius * 2 + 1) * (radius * 2 + 1)];
        for (int i = 0; i < data.length; i++) {
            data[i] = weight;
        }

        Kernel kernel = new Kernel(radius * 2 + 1, radius * 2 + 1, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);

        // Provjeri tip slike; ponekad je potrebno koristiti TYPE_INT_ARGB za filtere
        BufferedImage blurredImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : originalImage.getType());
        op.filter(originalImage, blurredImage);
        return blurredImage;
    }

    @Override
    public byte[] toByteArray(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(image, format, baos);
        if (!written) {
            throw new IOException("No ImageWriter found for format: " + format);
        }
        return baos.toByteArray();
    }

    @Override
    public String getOriginalImageFormat(byte[] imageBytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                return reader.getFormatName().toLowerCase();
            }
        }
        return null; // Nije moguće odrediti format
    }
}