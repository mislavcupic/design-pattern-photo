package hr.algebra.nrako.photoapp_backend.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImageProcessingOptions {
    private byte[] imageBytes;
    private Integer maxWidth;
    private Integer maxHeight;
    private String outputFormat;
    private boolean applySepia;
    private boolean applyBlur;
}
