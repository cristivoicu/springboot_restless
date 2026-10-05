package ro.cristivoicu.springbootrestless.fixtures.crate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules item 1 ("Atomic write pipeline"): before {@code create} ran inside a real
 * transaction, a write-response {@code Mapper} touching a lazy association failed under {@code
 * spring.jpa.open-in-view=false} - Spring Boot's own OSIV default of {@code true} (every other
 * test in this reactor runs under it) papers over exactly this, which is why this one test
 * deliberately overrides it.
 * <p>
 * Deliberately no {@code @Transactional} on the test itself, same reasoning as {@code
 * ConcurrentUpdateRaceTest}: Spring's test-rollback transaction binds one session for the whole
 * test method regardless of {@code open-in-view}, which would mask exactly the gap this test
 * exists to catch.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
class CreateWithLazyAssociationOsivOffTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PalletRepository palletRepository;

    @Test
    void createsWithALazyAssociationReadByTheResponseMapperUnderOpenInViewFalse() throws Exception {
        Pallet pallet = palletRepository.save(new Pallet(null, "Pallet-1"));

        CrateCreateModel create = new CrateCreateModel();
        create.setName("Box");
        create.setPalletId(pallet.getId());

        mockMvc.perform(post("/crates")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Box"))
                .andExpect(jsonPath("$.palletLabel").value("Pallet-1"));
    }
}
