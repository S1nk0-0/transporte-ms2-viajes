package pe.utec.transporte.ms2.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pe.utec.transporte.ms2.dto.ErrorDTO;

import java.util.stream.Collectors;

/**
 * Todos los errores salen como { "error": "...", "detalle": "..." } (Contrato §8).
 *
 * IMPORTANTE: extiende ResponseEntityExceptionHandler a proposito.
 * Sin eso, el @ExceptionHandler(Exception.class) de mas abajo gana sobre el
 * manejo propio de Spring y convierte en 500 lo que debe ser 404, 400 o 405
 * (ruta no mapeada, path variable no numerica, verbo HTTP no soportado).
 * ResponseEntityExceptionHandler declara handlers para esos tipos concretos,
 * que por especificidad ganan sobre Exception.class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Reescribe el cuerpo de TODA excepcion que maneja Spring, al formato del contrato. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest request) {
        return new ResponseEntity<>(new ErrorDTO(mensajePara(status), ex.getMessage()), headers, status);
    }

    private String mensajePara(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "datos invalidos";
            case 404 -> "no encontrado";
            case 405 -> "metodo no permitido";
            case 415 -> "tipo de contenido no soportado";
            default  -> "error en la peticion";
        };
    }

    /** Bean Validation sobre el body: detalle campo por campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return new ResponseEntity<>(new ErrorDTO("datos invalidos", detalle), headers, HttpStatus.BAD_REQUEST);
    }

    /** /ms2/viajes/abc  o  ?estado=noexiste  ->  400, no 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDTO> tipoInvalido(MethodArgumentTypeMismatchException e) {
        String esperado = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "?";
        return ResponseEntity.badRequest()
                .body(new ErrorDTO("datos invalidos",
                        e.getName() + ": '" + e.getValue() + "' no es un " + esperado + " valido"));
    }

    /** Errores de negocio explicitos: 404, 409, 422. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDTO> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorDTO(e.getMessage(), e.getDetalle()));
    }

    /**
     * Choques contra restricciones de la BD que la validacion no atrapo:
     * uk_paradas_viaje_orden, FK huerfana, valor fuera del DECIMAL. -> 409, no 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDTO> integridad(DataIntegrityViolationException e) {
        // El mensaje de MySQL nombra constraint, tabla y columna. Va al log, no al cliente:
        // este endpoint queda expuesto por el API Gateway.
        logger.warn("violacion de integridad: " + e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDTO("conflicto de datos",
                        "la operacion choca con una restriccion de los datos existentes"));
    }

    /** Ultimo recurso. Solo lo verdaderamente inesperado llega aca. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> general(Exception e) {
        logger.error("error no manejado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDTO("error interno", e.getClass().getSimpleName()));
    }
}
