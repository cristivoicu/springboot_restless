package ro.cristivoicu.springbootrestless.fixtures.rivet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultPatchDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the mass-assignment hole in {@code Default{Create,Update,Patch}DataSource}:
 * {@code BeanUtils.copyProperties} copies <em>every</em> matching-named property, including
 * {@code @Id}/{@code @Version}/{@code deleted}/audit-timestamp fields a DTO has no business
 * carrying at all. The {@code create} case is the sharpest version of this - a {@code
 * CreateModel} with a populated {@code id} makes {@code JpaRepository.save()} merge onto an
 * existing row instead of inserting a new one, entirely bypassing whatever {@code UPDATE} guard
 * that row would otherwise be checked against (see Ground rules item 6).
 */
@SpringBootTest
@Transactional
class MassAssignmentProtectionTest {

    @Autowired
    private RivetRepository repository;

    @Test
    void createIgnoresProtectedFieldsAndNeverMergesOntoAnExistingRow() throws Exception {
        Rivet existing = repository.saveAndFlush(new Rivet(null, "original", null, false, null, null));
        Long existingId = existing.getId();

        DefaultCreateDataSource<Rivet, Long, RivetCreateModel> dataSource =
                new DefaultCreateDataSource<>(repository, Rivet.class, RivetCreateModel.class);

        RivetCreateModel malicious = new RivetCreateModel();
        malicious.setName("hacked");
        malicious.setId(existingId); // attempt to merge onto the existing row instead of inserting
        malicious.setVersion(999L);
        malicious.setDeleted(true);
        malicious.setCreatedDate(Instant.EPOCH);
        malicious.setLastModifiedDate(Instant.EPOCH);

        Rivet created = dataSource.create(malicious);

        assertThat(created.getId()).isNotEqualTo(existingId);
        assertThat(created.isDeleted()).isFalse();
        assertThat(created.getVersion()).isNotEqualTo(999L);
        assertThat(created.getCreatedDate()).isNotEqualTo(Instant.EPOCH);
        assertThat(created.getLastModifiedDate()).isNotEqualTo(Instant.EPOCH);

        Rivet reloaded = repository.findById(existingId).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("original");
    }

    @Test
    void updateIgnoresProtectedFieldsButStillAppliesLegitimateOnes() throws Exception {
        Instant canary = Instant.parse("2020-01-01T00:00:00Z");
        Rivet entity = repository.saveAndFlush(new Rivet(null, "original", null, false, canary, canary));
        Long id = entity.getId();
        Long realVersion = entity.getVersion();

        DefaultUpdateDataSource<Rivet, Long, RivetUpdateModel> dataSource =
                new DefaultUpdateDataSource<>(repository, RivetUpdateModel.class);

        RivetUpdateModel malicious = new RivetUpdateModel();
        malicious.setName("updated");
        malicious.setId(999_999L);
        malicious.setVersion(999L);
        malicious.setDeleted(true);
        malicious.setCreatedDate(Instant.EPOCH);
        malicious.setLastModifiedDate(Instant.EPOCH);

        Rivet updated = dataSource.update(id, malicious);

        assertThat(updated.getName()).isEqualTo("updated"); // legitimate field still applied
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getVersion()).isNotEqualTo(999L).isEqualTo(realVersion);
        assertThat(updated.isDeleted()).isFalse();
        assertThat(updated.getCreatedDate()).isEqualTo(canary);
    }

    @Test
    void patchIgnoresProtectedFieldsButStillAppliesLegitimateOnes() throws Exception {
        Instant canary = Instant.parse("2020-01-01T00:00:00Z");
        Rivet entity = repository.saveAndFlush(new Rivet(null, "original", null, false, canary, canary));
        Long id = entity.getId();
        Long realVersion = entity.getVersion();

        DefaultPatchDataSource<Rivet, Long, RivetPatchModel> dataSource =
                new DefaultPatchDataSource<>(repository, RivetPatchModel.class);

        RivetPatchModel malicious = new RivetPatchModel();
        malicious.setName("patched");
        malicious.setId(999_999L);
        malicious.setVersion(999L);
        malicious.setDeleted(true);
        malicious.setCreatedDate(Instant.EPOCH);
        malicious.setLastModifiedDate(Instant.EPOCH);

        Rivet patched = dataSource.patch(id, malicious);

        assertThat(patched.getName()).isEqualTo("patched");
        assertThat(patched.getId()).isEqualTo(id);
        assertThat(patched.getVersion()).isNotEqualTo(999L).isEqualTo(realVersion);
        assertThat(patched.isDeleted()).isFalse();
        assertThat(patched.getCreatedDate()).isEqualTo(canary);
    }
}
