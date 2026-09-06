package pe.utec.transporte.ms2.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.utec.transporte.ms2.domain.EstadoViaje;
import pe.utec.transporte.ms2.domain.Viaje;

import java.time.Instant;

public interface ViajeRepository extends JpaRepository<Viaje, Integer> {

    @Query("""
           select v from Viaje v
           where (:pasajeroId  is null or v.pasajeroId = :pasajeroId)
             and (:conductorId is null or v.conductorId = :conductorId)
             and (:estado      is null or v.estado = :estado)
             and (:distritoOrigen  is null or v.distritoOrigen  = :distritoOrigen)
             and (:distritoDestino is null or v.distritoDestino = :distritoDestino)
             and (:desde is null or v.solicitadoEn >= :desde)
             and (:hasta is null or v.solicitadoEn <  :hasta)
           """)
    Page<Viaje> buscar(@Param("pasajeroId") Integer pasajeroId,
                       @Param("conductorId") Integer conductorId,
                       @Param("estado") EstadoViaje estado,
                       @Param("distritoOrigen") String distritoOrigen,
                       @Param("distritoDestino") String distritoDestino,
                       @Param("desde") Instant desde,
                       @Param("hasta") Instant hasta,
                       Pageable pageable);
}
