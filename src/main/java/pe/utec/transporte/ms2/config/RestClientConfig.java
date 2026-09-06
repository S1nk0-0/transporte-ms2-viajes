package pe.utec.transporte.ms2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient ms1RestClient(@Value("${ms2.ms1-url}") String ms1Url,
                                    @Value("${ms2.timeout-ms}") long timeoutMs) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(timeoutMs))
                .withReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(ms1Url)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
