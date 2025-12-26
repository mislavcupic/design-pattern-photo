package hr.algebra.nrako.photoapp_backend.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.util.Locale;
import java.util.Optional;

@Service
public class MetricsExportService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsExportService.class);
    private final MeterRegistry meterRegistry;
    private static final String CSV_FILE_PATH = "photo_app_metrics.csv";

    public MetricsExportService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeCsv();
    }

    private void initializeCsv() {
        File file = new File(CSV_FILE_PATH);
        if (!file.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE_PATH))) {
                // Točno 6 stupaca kako smo definirali
                writer.println("Timestamp,TotalUploads,SystemErrors,AvgProcessingTimeMS,JvmMemoryUsedMB,ActiveHttpRequests");
                logger.info("CSV datoteka uspješno inicijalizirana sa zaglavljima.");
            } catch (IOException e) {
                logger.error("Greška pri inicijalizaciji CSV datoteke", e);
            }
        }
    }

    @Scheduled(fixedRate = 60000) // Izvoz svake minute
    public void exportMetricsToCsv() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        // Dohvaćanje 5 metrika (3 custom + 2 standardne)
        double uploads = getCounterValue("photo.uploads.total");
        double errors = getCounterValue("photo.system.errors");
        double avgTime = getTimerMean("photo.processing.duration");

        double jvmMem = 0;
        try {
            jvmMem = meterRegistry.get("jvm.memory.used").gauge().value() / (1024 * 1024);
        } catch (Exception e) { jvmMem = 0; }

        double httpReqs = getCounterValue("http.server.requests");

        // Koristimo Locale.US da decimale budu točke (.), ne zarezi (,)
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE_PATH, true))) {
            writer.printf(Locale.US, "%s,%.0f,%.0f,%.2f,%.2f,%.0f%n",
                    timestamp, uploads, errors, avgTime, jvmMem, httpReqs);
            logger.info("Metrike zapisane u CSV.");
        } catch (IOException e) {
            logger.error("Greška pri pisanju u CSV", e);
        }
    }

    private double getCounterValue(String name) {
        return Optional.ofNullable(meterRegistry.find(name).counter())
                .map(Counter::count)
                .orElse(0.0);
    }

    private double getTimerMean(String name) {
        return Optional.ofNullable(meterRegistry.find(name).timer())
                .map(t -> t.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .orElse(0.0);
    }
}