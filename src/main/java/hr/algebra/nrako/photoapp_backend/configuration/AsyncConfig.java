package hr.algebra.nrako.photoapp_backend.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new DelegatingSecurityContextAsyncTaskExecutor(
                new
                        SimpleAsyncTaskExecutor()
        );
    }
}

