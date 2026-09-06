package pe.utec.transporte.ms2.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Contrato §8 · fechas exactamente "2026-08-14T21:58:40Z".
 *
 * spring.jackson.date-format NO aplica a los tipos de java.time: solo afecta a
 * java.util.Date. Instant se serializa con el modulo JSR-310, que emite los
 * nanosegundos que traiga el valor. Por eso la respuesta de un POST salia como
 * 2026-09-06T03:38:03.905407971Z y la de un GET salia bien: MySQL guarda
 * DATETIME sin fraccion, asi que al releer los nanos ya venian en cero.
 *
 * Este serializador fuerza el formato en la salida, venga de donde venga el valor.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter UTC_SEGUNDOS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer instantEnUtcSinFraccion() {
        return builder -> builder.serializerByType(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant valor, JsonGenerator gen, SerializerProvider sp) throws IOException {
                gen.writeString(UTC_SEGUNDOS.format(valor));
            }
        });
    }
}
