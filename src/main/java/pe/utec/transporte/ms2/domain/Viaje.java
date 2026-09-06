package pe.utec.transporte.ms2.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "viajes")
@Getter @Setter @NoArgsConstructor
public class Viaje {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;                      // 1..25000 (Contrato §3)

    /** usuarios.id de MS1 (PostgreSQL). Referencia logica, sin FK entre motores. */
    @Column(name = "pasajero_id", nullable = false)
    private Integer pasajeroId;

    /** conductores.id de MS1 */
    @Column(name = "conductor_id", nullable = false)
    private Integer conductorId;

    /** vehiculos.id de MS1 */
    @Column(name = "vehiculo_id", nullable = false)
    private Integer vehiculoId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tarifa_id", nullable = false)
    private Tarifa tarifa;                   // tarifas 1—N viajes

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoViaje estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago;

    @Column(name = "distrito_origen", nullable = false, length = 60)
    private String distritoOrigen;

    @Column(name = "distrito_destino", nullable = false, length = 60)
    private String distritoDestino;

    @Column(name = "direccion_origen", length = 160)
    private String direccionOrigen;

    @Column(name = "direccion_destino", length = 160)
    private String direccionDestino;

    @Column(name = "distancia_km", precision = 6, scale = 2)
    private BigDecimal distanciaKm;

    @Column(name = "duracion_min")
    private Integer duracionMin;

    @Column(name = "monto_total", precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "solicitado_en", nullable = false)
    private Instant solicitadoEn;

    @Column(name = "iniciado_en")
    private Instant iniciadoEn;

    @Column(name = "finalizado_en")
    private Instant finalizadoEn;

    /**
     * BatchSize evita el N+1 del listado: en vez de un SELECT por viaje, Hibernate
     * carga las paradas de hasta 50 viajes en una sola consulta con IN.
     * No se usa JOIN FETCH porque con paginacion obliga a paginar en memoria (HHH000104).
     */
    @OneToMany(mappedBy = "viaje", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @BatchSize(size = 50)
    private List<Parada> paradas = new ArrayList<>();   // viajes 1—N paradas

    public void agregarParada(Parada p) {
        p.setViaje(this);
        this.paradas.add(p);
    }
}
