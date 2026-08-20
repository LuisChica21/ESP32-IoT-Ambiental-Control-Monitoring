package ec.com.advanceit.iotmonitoreo.repository;

import ec.com.advanceit.iotmonitoreo.model.Measurement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementRepository extends JpaRepository<Measurement,Long> {

}
