package ro.cristivoicu.springbootrestless.fixtures.sprocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules Phase 2 item 10 ("Bulk performance"): {@code DefaultDeleteDataSource} converted
 * bulk-delete ids via a standalone {@code DefaultConversionService.getSharedInstance()},
 * independent of the app's own {@code ConversionService} bean - a custom {@code Converter} a
 * consumer registered was silently never consulted. Sprocket is processor-generated
 * ({@code allowAll = true}, no custom guard, so bulk delete is actually reachable - unlike
 * Doohickey, whose own fixture guard denies {@code DELETE_ALL} unconditionally), so its
 * generated resource now threads the real {@code ConversionService} through to {@code
 * DefaultDeleteDataSource}'s three-arg constructor. {@code @Import}s its own {@code
 * @TestConfiguration} so this custom converter only ever exists in this one test's own,
 * separately-cached Spring context - never polluting any other test's default context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BulkDeleteUsesRealConversionServiceTest.CountingConverterConfig.class)
class BulkDeleteUsesRealConversionServiceTest {

    @TestConfiguration
    static class CountingConverterConfig {
        @Bean
        Converter<String, Long> countingLongConverter() {
            return new CountingLongConverter();
        }
    }

    static class CountingLongConverter implements Converter<String, Long> {
        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public Long convert(String source) {
            INVOCATIONS.incrementAndGet();
            return Long.valueOf(source);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bulkDeleteConsultsTheAppsOwnRegisteredConverter() throws Exception {
        long id = createSprocket("Acme");
        CountingLongConverter.INVOCATIONS.set(0);

        mockMvc.perform(post("/sprockets/bulk-delete")
                        .contentType("application/json")
                        .content("{\"ids\":[\"" + id + "\"]}"))
                .andExpect(status().isNoContent());

        assertThat(CountingLongConverter.INVOCATIONS.get()).isGreaterThan(0);
    }

    private long createSprocket(String name) throws Exception {
        SprocketCreateModel create = new SprocketCreateModel();
        create.setName(name);
        String response = mockMvc.perform(post("/sprockets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
