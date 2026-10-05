package ro.cristivoicu.springbootrestless.fixtures.task;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ground rules item 2 ("Row-level authorization on writes") fixture: {@code ownerUsername} is
 * exactly the kind of owner/scoping field a write guard has to protect on both ends of a write -
 * not just "can this principal touch this row today" but "can the row they're submitting/leaving
 * behind still only belong to them". See {@code TaskAuthorizationGuard}/{@code
 * RowLevelWriteAuthorizationTest}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String ownerUsername;
}
