package pe.utec.transporte.ms2.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Contrato §2.2 · lo consulta el target group del ALB cada 30 s. No toca la BD. */
@RestController
@Tag(name = "health")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check del balanceador")
    public Map<String, String> health() {
        return Map.of("status", "ok", "servicio", "ms2");
    }
}
