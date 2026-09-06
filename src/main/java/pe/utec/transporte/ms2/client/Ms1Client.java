package pe.utec.transporte.ms2.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente hacia MS1 (Usuarios y Conductores), a traves del balanceador interno.
 * Contrato §5.1  · POST /ms2/viajes valida el pasajero y el conductor contra MS1.
 * Contrato §10.1 · timeout 5 s, un reintento; si MS1 no responde NO se devuelve 500.
 */
@Slf4j
@Component
public class Ms1Client {

    private final RestClient http;
    private final int reintentos;

    public Ms1Client(RestClient ms1RestClient,
                     @Value("${ms2.reintentos}") int reintentos) {
        this.http = ms1RestClient;
        this.reintentos = reintentos;
    }

    public enum Resultado { EXISTE, NO_EXISTE, NO_DISPONIBLE }

    public Resultado usuarioExiste(Integer id)   { return existe("/ms1/usuarios/" + id); }
    public Resultado conductorExiste(Integer id) { return existe("/ms1/conductores/" + id); }

    private Resultado existe(String ruta) {
        for (int intento = 0; intento <= reintentos; intento++) {
            try {
                http.get().uri(ruta).retrieve().toBodilessEntity();
                return Resultado.EXISTE;
            } catch (HttpClientErrorException.NotFound e) {
                return Resultado.NO_EXISTE;                 // 404 -> el llamador responde 422
            } catch (RestClientException e) {
                log.warn("MS1 {} intento {}/{}: {}", ruta, intento + 1, reintentos + 1, e.getMessage());
            }
        }
        return Resultado.NO_DISPONIBLE;                     // se degrada con advertencia
    }
}
