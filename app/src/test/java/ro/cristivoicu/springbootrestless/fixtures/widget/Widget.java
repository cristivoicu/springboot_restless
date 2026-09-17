package ro.cristivoicu.springbootrestless.fixtures.widget;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.datasource.SoftDeletable;

/**
 * Manual-tier fixture (see {@code WidgetRestlessResource}) proving two features together: a
 * {@code @Version} field (optimistic concurrency - see {@code RestlessResourceHandler#checkIfMatch}/
 * {@code RestlessExceptionHandler#handleOptimisticLock}) and {@link SoftDeletable} (flag-on-delete
 * via {@code DefaultSoftDeleteDataSource}, list-exclusion via {@code
 * RestlessResourceHandler#excludeSoftDeleted}). Lombok's {@code @Getter}/{@code @Setter} on a
 * primitive {@code boolean deleted} field already generate {@code isDeleted()}/{@code
 * setDeleted(boolean)} - exactly {@link SoftDeletable}'s two methods, so implementing it needs no
 * hand-written method bodies here at all.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Widget implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Version
    private Long version;

    private boolean deleted;
}
