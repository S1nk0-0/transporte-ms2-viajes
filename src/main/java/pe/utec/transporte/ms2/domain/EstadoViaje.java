package pe.utec.transporte.ms2.domain;

/** Contrato Cero §3 · estado_viaje */
public enum EstadoViaje {
    solicitado, en_curso, finalizado, cancelado;

    /** Transiciones permitidas por PATCH /ms2/viajes/{id}/estado */
    public boolean puedePasarA(EstadoViaje destino) {
        return switch (this) {
            case solicitado -> destino == en_curso || destino == cancelado;
            case en_curso   -> destino == finalizado || destino == cancelado;
            default         -> false;   // finalizado y cancelado son terminales
        };
    }
}
