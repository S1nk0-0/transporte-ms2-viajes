package pe.utec.transporte.ms2.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Contrato §2.3 · Swagger en /ms2/docs, con servers: apuntando al API Gateway. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ms2OpenApi(@Value("${ms2.public-url}") String publicUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("MS2 · Viajes")
                        .version("1.0.0")
                        .description("Microservicio de viajes de la plataforma de transporte urbano. "
                                   + "MySQL 8 · viajes_db. Consume MS1 para validar pasajero y conductor."))
                .servers(List.of(
                        new Server().url(publicUrl).description("API Gateway (HTTPS)"),
                        new Server().url("http://localhost:8002").description("local")
                ));
    }
}
