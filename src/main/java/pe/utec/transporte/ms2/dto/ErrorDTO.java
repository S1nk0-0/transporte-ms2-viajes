package pe.utec.transporte.ms2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Contrato Cero §8 · formato de error: { error, detalle } */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDTO(String error, String detalle) {
    public ErrorDTO(String error) { this(error, null); }
}
