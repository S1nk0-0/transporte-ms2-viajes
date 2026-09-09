package pe.utec.transporte.ms2.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.transporte.ms2.client.Ms1Client;
import pe.utec.transporte.ms2.domain.*;
import pe.utec.transporte.ms2.dto.*;
import pe.utec.transporte.ms2.error.ApiException;
import pe.utec.transporte.ms2.repo.TarifaRepository;
import pe.utec.transporte.ms2.repo.ViajeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ViajeService {

    private final ViajeRepository viajes;
    private final TarifaRepository tarifas;
    private final Ms1Client ms1;

    @Value("${ms2.validar-con-ms1}")
    private boolean validarConMs1;

    /** MySQL guarda DATETIME sin fraccion: truncamos para que lo escrito y lo devuelto coincidan. */
    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // ---------- lectura ----------

    @Transactional(readOnly = true)
    public PaginaDTO<ViajeDTO> listar(Integer pasajeroId, Integer conductorId, EstadoViaje estado,
                                      String distritoOrigen, String distritoDestino,
                                      Instant desde, Instant hasta, int page, int limit) {

        if (page < 1) page = 1;
        if (limit < 1) limit = 20;
        if (limit > 100) limit = 100;                         // Contrato §8 · limit maximo 100

        // solicitadoEn no es unico y ahora se trunca a segundos: sin desempatar por id
        // el orden entre empates es arbitrario y una fila puede repetirse entre paginas.
        Pageable p = PageRequest.of(page - 1, limit,
                Sort.by(Sort.Direction.DESC, "solicitadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Viaje> pagina = viajes.buscar(pasajeroId, conductorId, estado,
                                           distritoOrigen, distritoDestino, desde, hasta, p);

        return new PaginaDTO<>(pagina.getTotalElements(), page, limit,
                               pagina.getContent().stream().map(ViajeDTO::de).toList());
    }

    @Transactional(readOnly = true)
    public ViajeDTO obtener(Integer id) {
        return ViajeDTO.de(viajes.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("viaje " + id)));
    }

    // ---------- creacion (llamada inter-servicio) ----------

    /**
     * Sin @Transactional a proposito: la validacion contra MS1 puede tardar hasta 10 s
     * (timeout 5 s + un reintento) y con la transaccion abierta esos 10 s retienen una
     * conexion de Hikari sin usarla; con pool de 10, diez POST simultaneos contra un MS1
     * caido dejan al servicio sin BD. La atomicidad no se pierde: viajes.save() ya es
     * transaccional y el cascade de paradas (Viaje.paradas, CascadeType.ALL) viaja dentro
     * de ese mismo save.
     */
    public ViajeDTO crear(CrearViajeDTO in) {
        List<String> advertencias = new ArrayList<>();

        if (validarConMs1) {
            validarReferenciasEnMs1(in, advertencias);
        }

        Tarifa tarifa = tarifas.findById(in.tarifaId())
                .orElseThrow(() -> ApiException.noProcesable("tarifa_id " + in.tarifaId() + " no existe"));

        Viaje v = new Viaje();
        v.setPasajeroId(in.pasajeroId());
        v.setConductorId(in.conductorId());
        v.setVehiculoId(in.vehiculoId());
        v.setTarifa(tarifa);
        v.setEstado(EstadoViaje.solicitado);
        v.setMetodoPago(in.metodoPago());
        v.setDistritoOrigen(in.distritoOrigen());
        v.setDistritoDestino(in.distritoDestino());
        v.setDireccionOrigen(in.direccionOrigen());
        v.setDireccionDestino(in.direccionDestino());
        v.setDistanciaKm(in.distanciaKm());
        v.setDuracionMin(in.duracionMin());
        v.setSolicitadoEn(ahora());

        agregarParadas(v, in.paradas());

        return ViajeDTO.de(viajes.save(v), advertencias);
    }

    /**
     * Contrato §5.1  · si MS1 responde 404 -> 422.
     * Contrato §10.1 · si MS1 no responde -> se sigue, con UNA advertencia. Nunca 500.
     *
     * Si la primera llamada agota timeout y reintento, MS1 esta caido: no tiene sentido
     * gastar otros 10 s en la segunda. Cortar aqui deja el peor caso en ~10 s en vez de ~20 s,
     * y evita que la misma advertencia salga dos veces.
     */
    private void validarReferenciasEnMs1(CrearViajeDTO in, List<String> advertencias) {
        Ms1Client.Resultado pasajero = ms1.usuarioExiste(in.pasajeroId());
        if (pasajero == Ms1Client.Resultado.NO_EXISTE) {
            throw ApiException.noProcesable("pasajero_id " + in.pasajeroId() + " no existe en MS1");
        }
        if (pasajero == Ms1Client.Resultado.NO_DISPONIBLE) {
            advertencias.add("ms1 no disponible");
            return;                                   // MS1 caido: no se intenta la segunda llamada
        }

        Ms1Client.Resultado conductor = ms1.conductorExiste(in.conductorId());
        if (conductor == Ms1Client.Resultado.NO_EXISTE) {
            throw ApiException.noProcesable("conductor_id " + in.conductorId() + " no existe en MS1");
        }
        if (conductor == Ms1Client.Resultado.NO_DISPONIBLE) {
            advertencias.add("ms1 no disponible");
        }
    }

    /** El orden es unico por viaje (uk_paradas_viaje_orden). Se valida antes de tocar la BD. */
    private void agregarParadas(Viaje v, List<ParadaDTO> paradas) {
        if (paradas == null || paradas.isEmpty()) return;

        Set<Integer> ordenes = new HashSet<>();
        for (ParadaDTO pd : paradas) {
            if (!ordenes.add(pd.orden())) {
                throw ApiException.conflicto("orden de parada repetido: " + pd.orden());
            }
            Parada p = new Parada();
            p.setOrden(pd.orden());
            p.setDistrito(pd.distrito());
            p.setDireccion(pd.direccion());
            p.setLatitud(pd.latitud());
            p.setLongitud(pd.longitud());
            p.setLlegadaEn(pd.llegadaEn());
            v.agregarParada(p);
        }
    }

    // ---------- cambio de estado ----------

    @Transactional
    public ViajeDTO cambiarEstado(Integer id, CambiarEstadoDTO in) {
        Viaje v = viajes.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("viaje " + id));

        if (!v.getEstado().puedePasarA(in.estado())) {
            throw ApiException.conflicto("no se puede pasar de " + v.getEstado() + " a " + in.estado());
        }

        Instant momento = ahora();
        switch (in.estado()) {
            case en_curso -> v.setIniciadoEn(momento);
            case finalizado -> {
                if (in.distanciaKm() != null) v.setDistanciaKm(in.distanciaKm());
                if (in.duracionMin() != null) v.setDuracionMin(in.duracionMin());
                v.setFinalizadoEn(momento);
                v.setMontoTotal(calcularMonto(v));
            }
            case cancelado -> {
                v.setFinalizadoEn(momento);
                v.setMontoTotal(BigDecimal.ZERO.setScale(2));
            }
            default -> { }
        }
        v.setEstado(in.estado());
        return ViajeDTO.de(viajes.save(v));
    }

    /** Horas pico, en UTC. Identicas a HORAS_PICO de seed/seed_mysql.py:52 (repo MS3). */
    private static final Set<Integer> HORAS_PICO = Set.of(7, 8, 9, 18, 19, 20);

    /**
     * subtotal = tarifa_base + km*costo_por_km + min*costo_por_minuto
     * monto    = subtotal * (hora pico ? multiplicador_hora_pico : 1.00)
     *
     * El multiplicador se aplica al subtotal completo, no solo a la base: asi lo hace
     * seed/seed_mysql.py:65, y de esa formula salen los 25 000 montos sembrados.
     * El redondeo HALF_UP a 2 decimales va UNA sola vez, al final: redondear el
     * subtotal antes de multiplicar daria centimos de diferencia contra el seed.
     */
    private BigDecimal calcularMonto(Viaje v) {
        Tarifa t = v.getTarifa();
        BigDecimal km  = v.getDistanciaKm() != null ? v.getDistanciaKm() : BigDecimal.ZERO;
        BigDecimal min = v.getDuracionMin() != null ? BigDecimal.valueOf(v.getDuracionMin()) : BigDecimal.ZERO;
        BigDecimal subtotal = t.getTarifaBase()
                .add(km.multiply(t.getCostoPorKm()))
                .add(min.multiply(t.getCostoPorMinuto()));

        return subtotal.multiply(multiplicadorDe(v, t)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * El seed evalua la hora sobre `iniciado` (seed_mysql.py:60 y :64), o sea la columna
     * iniciado_en, y sus datetime son naive sobre una BD con time_zone = '+00:00'
     * (01-schema.sql:12): son UTC. Aca se lee el mismo campo y se fuerza UTC en vez de la
     * zona del contenedor, porque una MV con otro TZ correria la franja pico y los montos
     * dejarian de cuadrar con lo sembrado.
     *
     * iniciado_en puede venir null: la columna es NULL-able y una fila cargada por fuera
     * puede estar en_curso sin ella. Se cae a solicitado_en, que es NOT NULL. En los datos
     * del seed la espera entre una y otra es de 1 a 9 minutos (seed_mysql.py:59), asi que
     * es el proxy mas cercano; solo difiere si solicitud e inicio caen en horas distintas.
     * La alternativa era asumir 1.00, pero eso deja de cobrar el pico sin dejar rastro.
     */
    private BigDecimal multiplicadorDe(Viaje v, Tarifa t) {
        Instant momento = v.getIniciadoEn() != null ? v.getIniciadoEn() : v.getSolicitadoEn();
        int hora = momento.atZone(ZoneOffset.UTC).getHour();
        return HORAS_PICO.contains(hora) ? t.getMultiplicadorHoraPico() : BigDecimal.ONE;
    }

    // ---------- tarifas ----------

    @Transactional(readOnly = true)
    public List<TarifaDTO> listarTarifas() {
        return tarifas.findAll(Sort.by("id")).stream().map(TarifaDTO::de).toList();
    }
}
