package pe.utec.transporte.ms2.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "paradas",
       uniqueConstraints = @UniqueConstraint(name = "uk_paradas_viaje_orden",
                                             columnNames = {"viaje_id", "orden"}))
@Getter @Setter @NoArgsConstructor
public class Parada {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viaje_id", nullable = false)
    private Viaje viaje;

    @Column(nullable = false)
    private Integer orden;                   // 1, 2, 3... dentro del viaje

    @Column(nullable = false, length = 60)
    private String distrito;

    @Column(length = 160)
    private String direccion;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitud;

    @Column(name = "llegada_en")
    private Instant llegadaEn;
}
