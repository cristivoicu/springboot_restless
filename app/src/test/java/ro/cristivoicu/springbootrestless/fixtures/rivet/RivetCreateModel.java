package ro.cristivoicu.springbootrestless.fixtures.rivet;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

import java.time.Instant;

/**
 * Deliberately declares every protected-field name alongside the legitimate {@code name} - a
 * hostile client sending {@code id}/{@code version}/{@code deleted}/{@code createdDate}/
 * {@code lastModifiedDate} in a create request body is exactly what {@code
 * MassAssignmentProtectionTest} proves {@code DefaultCreateDataSource} must not copy onto the
 * new entity.
 */
@Getter
@Setter
public class RivetCreateModel implements CreateModel {
    private String name;
    private Long id;
    private Long version;
    private boolean deleted;
    private Instant createdDate;
    private Instant lastModifiedDate;
}
