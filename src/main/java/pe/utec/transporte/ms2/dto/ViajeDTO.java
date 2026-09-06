package pe.utec.transporte.ms2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import pe.utec.transporte.ms2.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ViajeDTO(Integer id,
                       Integer pasajeroId,
                       Integer conductorId,
                       Integer vehiculoId,
                       Integer tarifaId,
                       TipoServicio tipoServicio,
                       EstadoViaje estado,
                       MetodoPago metodoPago,
                       String distritoOrigen,
                       String distritoDestino,
                       String direccionOrigen,
                       String direccionDestino,
                       BigDecimal distanciaKm,
                       Integer duracionMin,
                       BigDecimal montoTotal,
                       Instant solicitadoEn,
                       Instant iniciadoEn,
                       Instant finalizadoEn,
                       List<ParadaDTO> paradas,
                       /** Contrato §10.1 · solo aparece si un servicio llamado no respondio */
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> advertencias) {

    public static ViajeDTO de(Viaje v) { return de(v, null); }

    public static ViajeDTO de(Viaje v, List<String> advertencias) {
        return new ViajeDTO(
                v.getId(), v.getPasajeroId(), v.getConductorId(), v.getVehiculoId(),
                v.getTarifa().getId(), v.getTarifa().getTipoServicio(),
                v.getEstado(), v.getMetodoPago(),
                v.getDistritoOrigen(), v.getDistritoDestino(),
                v.getDireccionOrigen(), v.getDireccionDestino(),
                v.getDistanciaKm(), v.getDuracionMin(), v.getMontoTotal(),
                v.getSolicitadoEn(), v.getIniciadoEn(), v.getFinalizadoEn(),
                v.getParadas().stream().map(ParadaDTO::de).toList(),
                advertencias);
    }
}
