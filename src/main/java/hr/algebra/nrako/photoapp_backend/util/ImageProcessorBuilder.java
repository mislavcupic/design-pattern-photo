package hr.algebra.nrako.photoapp_backend.util;

import hr.algebra.nrako.photoapp_backend.domain.ImageProcessingOptions;

public interface ImageProcessorBuilder {

    ImageProcessorBuilder withImageBytes(byte[] imageBytes);

    ImageProcessorBuilder resize(Integer maxWidth, Integer maxHeight);

    ImageProcessorBuilder format(String outputFormat);

    ImageProcessorBuilder sepia(boolean apply);
    ImageProcessorBuilder blur(boolean apply);

    ImageProcessingOptions build();
    void reset();
}