package pe.utec.transporte.ms2.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tarifas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Tarifa {

    @Id
    private Integer id;                      // 1..4, fijos (Contrato §3)

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_servicio", nullable = false, length = 20)
    private TipoServicio tipoServicio;

    @Column(name = "tarifa_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal tarifaBase;

    @Column(name = "costo_por_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoPorKm;

    @Column(name = "costo_por_minuto", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoPorMinuto;

    /** Multiplicador del subtotal en hora pico (1.20..1.60), no un recargo que se suma. */
    @Column(name = "multiplicador_hora_pico", nullable = false, precision = 10, scale = 2)
    private BigDecimal multiplicadorHoraPico;

    @Column(nullable = false)
    private Boolean activa;
}
