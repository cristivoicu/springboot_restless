package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface GizmoRepository extends SpecificationRepository<Gizmo, Long> {
}
