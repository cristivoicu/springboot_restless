package ro.cristivoicu.springbootrestless.fixtures.racer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ground rules item 1 ("Atomic write pipeline") fixture: {@code @Version} is what turns "two
 * requests race between the guard check and the write" into an observable {@code 409}/{@code 412}
 * instead of a silent overwrite - see {@code ConcurrentUpdateRaceTest}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Racer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Version
    private Long version;
}
