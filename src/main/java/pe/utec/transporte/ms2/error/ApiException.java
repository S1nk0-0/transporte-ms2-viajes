package pe.utec.transporte.ms2.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String detalle;

    public ApiException(HttpStatus status, String mensaje, String detalle) {
        super(mensaje);
        this.status = status;
        this.detalle = detalle;
    }

    public static ApiException noEncontrado(String q)  { return new ApiException(HttpStatus.NOT_FOUND, "no encontrado", q); }
    public static ApiException conflicto(String q)     { return new ApiException(HttpStatus.CONFLICT, "conflicto de estado", q); }
    /** Contrato §5.1 · MS1 dice 404 sobre el usuario o el conductor -> 422 */
    public static ApiException noProcesable(String q)  { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "referencia invalida", q); }
}
