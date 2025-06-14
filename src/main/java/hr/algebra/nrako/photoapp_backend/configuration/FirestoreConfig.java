package hr.algebra.nrako.photoapp_backend.configuration;


import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirestoreConfig {
    @Value("${firebase.config.path}")
    private Resource firebaseConfigResource;
    @Bean
    public Firestore firestore() throws IOException {
        InputStream serviceAccount = firebaseConfigResource.getInputStream();

        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);
        FirestoreOptions options = FirestoreOptions.newBuilder()
                .setCredentials(credentials)
                .build();
        return options.getService();
    }
}