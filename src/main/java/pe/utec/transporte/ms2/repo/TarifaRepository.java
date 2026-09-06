package pe.utec.transporte.ms2.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.utec.transporte.ms2.domain.Tarifa;

public interface TarifaRepository extends JpaRepository<Tarifa, Integer> { }
