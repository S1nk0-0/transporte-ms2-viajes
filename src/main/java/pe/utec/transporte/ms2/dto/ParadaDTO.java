package pe.utec.transporte.ms2.dto;

import jakarta.validation.constraints.*;
import pe.utec.transporte.ms2.domain.Parada;

import java.math.BigDecimal;
import java.time.Instant;

public record ParadaDTO(Long id,
                        @NotNull @Min(1) Integer orden,
                        @NotBlank @Size(max = 60) String distrito,
                        @Size(max = 160) String direccion,
                        BigDecimal latitud,
                        BigDecimal longitud,
                        Instant llegadaEn) {

    public static ParadaDTO de(Parada p) {
        return new ParadaDTO(p.getId(), p.getOrden(), p.getDistrito(), p.getDireccion(),
                             p.getLatitud(), p.getLongitud(), p.getLlegadaEn());
    }
}
