package pe.utec.transporte.ms2.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.utec.transporte.ms2.domain.EstadoViaje;
import pe.utec.transporte.ms2.dto.*;
import pe.utec.transporte.ms2.service.ViajeService;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/viajes")
@RequiredArgsConstructor
@Tag(name = "viajes")
public class ViajeController {

    private final ViajeService service;

    @GetMapping
    @Operation(summary = "Listar viajes con filtros y paginacion")
    public PaginaDTO<ViajeDTO> listar(
            @RequestParam(required = false) Integer pasajeroId,
            @RequestParam(required = false) Integer conductorId,
            @RequestParam(required = false) EstadoViaje estado,
            @RequestParam(required = false) String distritoOrigen,
            @RequestParam(required = false) String distritoDestino,
            @Parameter(description = "ISO 8601 UTC, ej. 2026-03-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        return service.listar(pasajeroId, conductorId, estado,
                              distritoOrigen, distritoDestino, desde, hasta, page, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un viaje con sus paradas")
    @ApiResponses({@ApiResponse(responseCode = "200"), @ApiResponse(responseCode = "404")})
    public ViajeDTO obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crear un viaje. Valida pasajero_id y conductor_id contra MS1 (Contrato §5.1)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "creado"),
        @ApiResponse(responseCode = "400", description = "datos invalidos"),
        @ApiResponse(responseCode = "422", description = "MS1 respondio 404 sobre el usuario o el conductor")
    })
    public ResponseEntity<ViajeDTO> crear(@Valid @RequestBody CrearViajeDTO in) {
        ViajeDTO creado = service.crear(in);
        return ResponseEntity.created(URI.create("/ms2/viajes/" + creado.id())).body(creado);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar el estado del viaje",
               description = "solicitado -> en_curso | cancelado ; en_curso -> finalizado | cancelado. "
                           + "Al finalizar se calcula monto_total con la tarifa del viaje.")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "404"),
        @ApiResponse(responseCode = "409", description = "transicion de estado no permitida")
    })
    public ViajeDTO cambiarEstado(@PathVariable Integer id, @Valid @RequestBody CambiarEstadoDTO in) {
        return service.cambiarEstado(id, in);
    }
}
