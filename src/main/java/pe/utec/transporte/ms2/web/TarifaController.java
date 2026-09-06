package pe.utec.transporte.ms2.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.utec.transporte.ms2.dto.TarifaDTO;
import pe.utec.transporte.ms2.service.ViajeService;

import java.util.List;

@RestController
@RequestMapping("/tarifas")
@RequiredArgsConstructor
@Tag(name = "tarifas")
public class TarifaController {

    private final ViajeService service;

    @GetMapping
    @Operation(summary = "Listar las 4 tarifas del catalogo")
    public List<TarifaDTO> listar() {
        return service.listarTarifas();
    }
}
