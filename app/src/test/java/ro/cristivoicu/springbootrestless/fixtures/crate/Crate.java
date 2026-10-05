package ro.cristivoicu.springbootrestless.fixtures.crate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code pallet} is deliberately {@code LAZY} - {@code CrateMapper} touching it is what proves
 * {@code RestlessResourceHandler#create}/{@code #update} need a real transaction boundary around
 * the whole write-then-map sequence (Ground rules item 1): with {@code
 * spring.jpa.open-in-view=false}, a lazy association is only initializable while the write's own
 * transaction/session is still open.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Crate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Pallet pallet;
}
