package pe.utec.transporte.ms2.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.utec.transporte.ms2.domain.MetodoPago;

import java.math.BigDecimal;
import java.util.List;

public record CrearViajeDTO(
        @NotNull @Min(1) Integer pasajeroId,
        @NotNull @Min(1) Integer conductorId,
        @NotNull @Min(1) Integer vehiculoId,

        // Sin @Max: una tarifa inexistente debe llegar al servicio y salir como 422
        // ("referencia invalida"), no como 400. Con @Max(4) el 422 era codigo muerto.
        @NotNull @Min(1) Integer tarifaId,

        @NotNull MetodoPago metodoPago,
        @NotBlank @Size(max = 60) String distritoOrigen,
        @NotBlank @Size(max = 60) String distritoDestino,
        @Size(max = 160) String direccionOrigen,
        @Size(max = 160) String direccionDestino,

        // DECIMAL(6,2) en la BD: 4 enteros + 2 decimales. Sin esto, 99999.99 pasaba
        // la validacion y reventaba contra MySQL como 500.
        @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal distanciaKm,

        @Min(0) @Max(1440) Integer duracionMin,
        @Valid List<ParadaDTO> paradas) { }
