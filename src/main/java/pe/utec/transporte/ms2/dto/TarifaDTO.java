package pe.utec.transporte.ms2.dto;

import pe.utec.transporte.ms2.domain.Tarifa;
import pe.utec.transporte.ms2.domain.TipoServicio;

import java.math.BigDecimal;

public record TarifaDTO(Integer id,
                        TipoServicio tipoServicio,
                        BigDecimal tarifaBase,
                        BigDecimal costoPorKm,
                        BigDecimal costoPorMinuto,
                        BigDecimal recargoHoraPico,
                        Boolean activa) {

    public static TarifaDTO de(Tarifa t) {
        return new TarifaDTO(t.getId(), t.getTipoServicio(), t.getTarifaBase(),
                             t.getCostoPorKm(), t.getCostoPorMinuto(),
                             t.getRecargoHoraPico(), t.getActiva());
    }
}
