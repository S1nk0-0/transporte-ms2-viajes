package pe.utec.transporte.ms2.dto;

import java.util.List;

/** Contrato Cero §8 · formato de listado: { total, page, limit, items } */
public record PaginaDTO<T>(long total, int page, int limit, List<T> items) { }
