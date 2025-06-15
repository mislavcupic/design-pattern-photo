package hr.algebra.nrako.photoapp_backend.dto;

import hr.algebra.nrako.photoapp_backend.domain.Photo; // Važno!
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoDownloadResponse {
    private byte[] bytes;
    private String fileName;
    private Photo photo; // Možeš poslati cijeli Photo objekt, ili samo PhotoDto ako ne želiš ovisnost o domain
}