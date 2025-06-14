package hr.algebra.nrako.photoapp_backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public interface ImageProcessingService {

    /**
     * Procesira sliku primjenjujući zadane filtere i opcije.
     *
     * @param originalImageBytes Izvorni bajtovi slike.
     * @param maxWidth Maksimalna širina za promjenu veličine (null za ignoriranje).
     * @param maxHeight Maksimalna visina za promjenu veličine (null za ignoriranje).
     * @param outputFormat Željeni izlazni format (npr. "png", "jpeg", "bmp"). Null za originalni format.
     * @param applySepia Treba li primijeniti sepia filter.
     * @param applyBlur Treba li primijeniti blur filter.
     * @return Bajtovi procesirane slike.
     * @throws IOException Ako dođe do greške prilikom čitanja/pisanja slike.
     */
    byte[] processImage(byte[] originalImageBytes, Integer maxWidth, Integer maxHeight, String outputFormat, boolean applySepia, boolean applyBlur) throws IOException;

    /**
     * Mijenja veličinu slike.
     * @param originalImage Izvorna slika.
     * @param targetWidth Ciljana širina.
     * @param targetHeight Ciljana visina.
     * @return Sliku promijenjene veličine.
     */
    BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight);

    /**
     * Primjenjuje sepia filter na sliku.
     * @param originalImage Izvorna slika.
     * @return Sliku sa sepia filterom.
     */
    BufferedImage applySepiaFilter(BufferedImage originalImage);

    /**
     * Primjenjuje blur (zamagljenje) filter na sliku.
     * @param originalImage Izvorna slika.
     * @return Sliku sa blur filterom.
     */
    BufferedImage applyBlurFilter(BufferedImage originalImage);

    /**
     * Konvertira BufferedImage u niz bajtova određenog formata.
     * @param image Slika za konverziju.
     * @param format Željeni format ("png", "jpeg", "bmp").
     * @return Niz bajtova slike.
     * @throws IOException Ako dođe do greške prilikom konverzije.
     */
    byte[] toByteArray(BufferedImage image, String format) throws IOException;

    /**
     * Pomaže u određivanju originalnog formata slike iz njenih bajtova.
     * @param imageBytes Bajtovi slike.
     * @return Format slike kao String (npr. "jpeg", "png").
     * @throws IOException ako se format ne može odrediti.
     */
    String getOriginalImageFormat(byte[] imageBytes) throws IOException;
}