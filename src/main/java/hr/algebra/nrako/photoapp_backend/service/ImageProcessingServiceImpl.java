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
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.*;
import java.util.Iterator;

@Service
public class ImageProcessingServiceImpl implements ImageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingServiceImpl.class);
    private static final int MARK_READ_LIMIT = 1024 * 1024; // 1MB buffer za detekciju formata

    @Override
    public void processImage(InputStream inputStream, OutputStream outputStream,
                             Integer maxWidth, Integer maxHeight, String outputFormat,
                             boolean applySepia, boolean applyBlur) throws IOException {

        // 1. Priprema streama za višekratno čitanje (mark/reset)
        BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
        bufferedInput.mark(MARK_READ_LIMIT);

        // 2. Detekcija originalnog formata prije čitanja cijele slike
        String detectedFormat = getOriginalImageFormat(bufferedInput);
        bufferedInput.reset(); // Vraćamo se na početak streama

        // 3. Čitanje slike u BufferedImage
        BufferedImage image = ImageIO.read(bufferedInput);
        if (image == null) {
            throw new IOException("Nije moguće pročitati sliku. Format: " + detectedFormat);
        }

        BufferedImage processedImage = image;

        // 4. Promjena veličine (Resize)
        if (maxWidth != null || maxHeight != null) {
            processedImage = resizeImageInternal(processedImage, maxWidth, maxHeight);
        }

        // 5. Primjena filtera
        if (applySepia) {
            processedImage = applySepiaFilter(processedImage);
        }
        if (applyBlur) {
            processedImage = applyBlurFilter(processedImage);
        }

        // 6. Određivanje izlaznog formata i pisanje u OutputStream
        String finalFormat = (outputFormat != null && !outputFormat.isEmpty())
                ? outputFormat.toLowerCase()
                : (detectedFormat != null ? detectedFormat : "png");

        boolean written = ImageIO.write(processedImage, finalFormat, outputStream);
        if (!written) {
            throw new IOException("ImageIO nije pronašao writer za format: " + finalFormat);
        }

        outputStream.flush();
        logger.info("Slika uspješno procesirana i poslana u stream. Format: {}", finalFormat);
    }

    private BufferedImage resizeImageInternal(BufferedImage originalImage, Integer maxWidth, Integer maxHeight) throws IOException {
        int currentWidth = originalImage.getWidth();
        int currentHeight = originalImage.getHeight();

        int targetWidth = maxWidth != null ? maxWidth : currentWidth;
        int targetHeight = maxHeight != null ? maxHeight : currentHeight;

        // Thumbnailator automatski održava aspect ratio ako koristimo .size()
        return Thumbnails.of(originalImage)
                .size(targetWidth, targetHeight)
                .asBufferedImage();
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

                sepiaImage.setRGB(x, y, (Math.min(tr, 255) << 16) | (Math.min(tg, 255) << 8) | Math.min(tb, 255));
            }
        }
        return sepiaImage;
    }

    @Override
    public BufferedImage applyBlurFilter(BufferedImage originalImage) {
        float[] matrix = new float[49]; // 7x7 kernel za jači blur
        for (int i = 0; i < 49; i++) matrix[i] = 1.0f / 49.0f;

        Kernel kernel = new Kernel(7, 7, matrix);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);

        BufferedImage dest = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), originalImage.getType());
        return op.filter(originalImage, dest);
    }

    @Override
    public String getOriginalImageFormat(InputStream is) throws IOException {
        // ImageIO.createImageInputStream ne zatvara originalni InputStream, što nam treba za reset()
        try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                String format = reader.getFormatName().toLowerCase();
                reader.dispose();
                return format;
            }
        }
        return null;
    }

    // --- Ove metode su ostavljene radi kompatibilnosti s interfaceom ako zatrebaju ---
    @Override
    public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        try {
            return Thumbnails.of(originalImage).size(targetWidth, targetHeight).asBufferedImage();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


}

/*

@Service
public class ImageProcessingServiceImpl implements ImageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingServiceImpl.class);

    @Override
    public byte[] processImage(byte[] originalImageBytes, Integer maxWidth, Integer maxHeight, String outputFormat, boolean applySepia, boolean applyBlur) throws IOException {
        // Logiraj format prije čitanja
        String detectedFormat = getOriginalImageFormat(originalImageBytes);
        logger.info("Pokušavam pročitati sliku format: {}", detectedFormat);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalImageBytes));
        if (image == null) {
            // Dodaj više detalja u iznimku
            throw new IOException("Failed to read image bytes into BufferedImage. Format detected: " + detectedFormat + ". Provjeri ImageIO plugine (npr. TwelveMonkeys).");
        }

        BufferedImage processedImage = image;

        // 1. Promjena veličine
        // Ovdje provjeravamo je li barem jedna dimenzija zadana za resize
        if (maxWidth != null || maxHeight != null) {
            int currentWidth = processedImage.getWidth();
            int currentHeight = processedImage.getHeight();
            int targetWidth = maxWidth != null ? maxWidth : currentWidth;
            int targetHeight = maxHeight != null ? maxHeight : currentHeight;

            // Ako je zadana samo jedna dimenzija, izračunaj drugu da zadržiš omjer
            if (maxWidth != null && maxHeight == null) {
                targetHeight = (int) (currentHeight * ((double) targetWidth / currentWidth));
            } else if (maxHeight != null && maxWidth == null) {
                targetWidth = (int) (currentWidth * ((double) targetHeight / currentHeight));
            }
            // Ako su oba null, nema resizea. Ako su oba zadana, koriste se direktno.

            // Samo ako je neka dimenzija promijenjena ili ako treba forsirati resize
            if (targetWidth != currentWidth || targetHeight != currentHeight) {
                processedImage = resizeImage(processedImage, targetWidth, targetHeight);
            }
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
            finalOutputFormat = detectedFormat; // Koristi već detektirani format
            if (finalOutputFormat == null) {
                finalOutputFormat = "png"; // Fallback ako se originalni format ne može odrediti
            }
        }
        // Osiguraj da je format malim slovima jer ImageIO.write očekuje to
        finalOutputFormat = finalOutputFormat.toLowerCase();

        return toByteArray(processedImage, finalOutputFormat);
    }

    @Override
    public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        try {
            // Direktno koristi Thumbnailator za resize i vrati BufferedImage
            // Koristimo .asBufferedImage() za dobivanje BufferedImage objekta
            return Thumbnails.of(originalImage)
                    .size(targetWidth, targetHeight)
                    .asBufferedImage();
        } catch (IOException e) {
            logger.error("Failed to resize image using Thumbnailator.", e);
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

        // Kreiraj novu sliku s ARGB tipom ako originalna nema alfu ili je nepoznat tip,
        // jer neki filteri bolje rade s njim.
        BufferedImage blurredImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                originalImage.getType() == BufferedImage.TYPE_CUSTOM || originalImage.getType() == 0 ?
                        BufferedImage.TYPE_INT_ARGB : originalImage.getType()
        );
        op.filter(originalImage, blurredImage);
        return blurredImage;
    }

    @Override
    public byte[] toByteArray(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(image, format, baos);
        if (!written) {
            // Možeš dodati logiranje dostupnih ImageWriter-a ovdje za debug
            // Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
            // while(writers.hasNext()) {
            //     logger.warn("Dostupan writer za {}: {}", format, writers.next().getClass().getName());
            // }
            throw new IOException("No ImageWriter found for format: " + format + ". Provjeri podršku za format.");
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
*/
