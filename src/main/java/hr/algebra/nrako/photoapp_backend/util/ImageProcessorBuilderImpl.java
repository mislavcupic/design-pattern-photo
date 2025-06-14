package hr.algebra.nrako.photoapp_backend.util;

import hr.algebra.nrako.photoapp_backend.domain.ImageProcessingOptions;

import org.springframework.stereotype.Component;

@Component
public class ImageProcessorBuilderImpl implements ImageProcessorBuilder {

    private byte[] imageBytes;
    private Integer maxWidth;
    private Integer maxHeight;
    private String outputFormat;
    private boolean applySepia;
    private boolean applyBlur;

    @Override
    public ImageProcessorBuilder withImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
        return this;
    }

    @Override
    public ImageProcessorBuilder resize(Integer maxWidth, Integer maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        return this;
    }

    @Override
    public ImageProcessorBuilder format(String outputFormat) {
        this.outputFormat = outputFormat;
        return this;
    }

    @Override
    public ImageProcessorBuilder sepia(boolean apply) {
        this.applySepia = apply;
        return this;
    }

    @Override
    public ImageProcessorBuilder blur(boolean apply) {
        this.applyBlur = apply;
        return this;
    }

    @Override
    public ImageProcessingOptions build() {
        return new ImageProcessingOptions(imageBytes, maxWidth, maxHeight, outputFormat, applySepia, applyBlur);
    }

    public void reset() {
        this.imageBytes = null;
        this.maxWidth = null;
        this.maxHeight = null;
        this.outputFormat = null;
        this.applySepia = false;
        this.applyBlur = false;
    }
}