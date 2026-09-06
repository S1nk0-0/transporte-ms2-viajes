package pe.utec.transporte.ms2.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import pe.utec.transporte.ms2.domain.EstadoViaje;

import java.math.BigDecimal;

/** Body de PATCH /ms2/viajes/{id}/estado */
public record CambiarEstadoDTO(@NotNull EstadoViaje estado,
                               /** opcionales, solo se usan al pasar a finalizado */
                               @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal distanciaKm,
                               @Min(0) @Max(1440) Integer duracionMin) { }
