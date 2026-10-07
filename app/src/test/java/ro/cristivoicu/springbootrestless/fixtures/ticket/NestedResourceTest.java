package ro.cristivoicu.springbootrestless.fixtures.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature B (nested resources), scalar-FK form - {@link Ticket#getBoardId()} is a flat
 * {@code UUID} column, the same shape backend-nunti's own real integration needed ({@code
 * Guest.eventId}). The dotted {@code association.id} form is covered separately by {@code
 * example}'s {@code Task} ({@code project.id}, a real {@code @ManyToOne}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NestedResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createIgnoresABodySuppliedBoardIdAndUsesThePathInstead() throws Exception {
        UUID pathBoard = UUID.randomUUID();
        UUID bodyBoard = UUID.randomUUID();

        TicketCreateModel create = new TicketCreateModel();
        create.setTitle("Fix the thing");
        create.setBoardId(bodyBoard); // deliberately different from the path - must be ignored

        mockMvc.perform(post("/boards/{boardId}/tickets", pathBoard)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Fix the thing"))
                .andExpect(jsonPath("$.boardId").value(pathBoard.toString()));
    }

    @Test
    void listOnlyReturnsThePathBoardsOwnTickets() throws Exception {
        UUID boardA = UUID.randomUUID();
        UUID boardB = UUID.randomUUID();
        createTicket(boardA, "A's ticket");
        createTicket(boardB, "B's ticket");

        mockMvc.perform(get("/boards/{boardId}/tickets", boardA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].title").value("A's ticket"));
    }

    @Test
    void getOfAForeignBoardsTicket404s() throws Exception {
        UUID boardA = UUID.randomUUID();
        UUID boardB = UUID.randomUUID();
        long ticketId = createTicket(boardA, "A's ticket");

        mockMvc.perform(get("/boards/{boardId}/tickets/{id}", boardB, ticketId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/boards/{boardId}/tickets/{id}", boardA, ticketId))
                .andExpect(status().isOk());
    }

    @Test
    void updateCanNeverMoveATicketToADifferentBoard() throws Exception {
        UUID boardA = UUID.randomUUID();
        UUID boardC = UUID.randomUUID();
        long ticketId = createTicket(boardA, "Original");

        TicketUpdateModel update = new TicketUpdateModel();
        update.setTitle("Renamed");
        update.setBoardId(boardC); // an attempt to move it - must be ignored

        mockMvc.perform(put("/boards/{boardId}/tickets/{id}", boardA, ticketId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.boardId").value(boardA.toString()));

        // Still addressable under its original board, not the one the update body asked for.
        mockMvc.perform(get("/boards/{boardId}/tickets/{id}", boardA, ticketId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/boards/{boardId}/tickets/{id}", boardC, ticketId))
                .andExpect(status().isNotFound());
    }

    @Test
    void unconvertiblePathVariableIs400() throws Exception {
        mockMvc.perform(get("/boards/{boardId}/tickets", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkUpdateWithOneForeignTicketFailsTheWholeBatchAndWritesNothing() throws Exception {
        UUID boardA = UUID.randomUUID();
        UUID boardB = UUID.randomUUID();
        long ownTicket = createTicket(boardA, "Own");
        long foreignTicket = createTicket(boardB, "Foreign");

        String body = """
                {"%d": {"title": "Hijacked own"}, "%d": {"title": "Hijacked foreign"}}
                """.formatted(ownTicket, foreignTicket);

        mockMvc.perform(put("/boards/{boardId}/tickets/bulk", boardA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/boards/{boardId}/tickets/{id}", boardA, ownTicket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Own"));
    }

    @Test
    void bulkDeleteWithOneForeignTicketFailsTheWholeBatchAndDeletesNothing() throws Exception {
        UUID boardA = UUID.randomUUID();
        UUID boardB = UUID.randomUUID();
        long ownTicket = createTicket(boardA, "Own");
        long foreignTicket = createTicket(boardB, "Foreign");

        String body = "{\"ids\": [\"" + ownTicket + "\", \"" + foreignTicket + "\"]}";
        mockMvc.perform(post("/boards/{boardId}/tickets/bulk-delete", boardA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/boards/{boardId}/tickets/{id}", boardA, ownTicket))
                .andExpect(status().isOk());
    }

    @Test
    void deleteOfAForeignBoardsTicket404sAndDeletesNothing() throws Exception {
        UUID boardA = UUID.randomUUID();
        UUID boardB = UUID.randomUUID();
        long ticketId = createTicket(boardA, "A's ticket");

        mockMvc.perform(delete("/boards/{boardId}/tickets/{id}", boardB, ticketId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/boards/{boardId}/tickets/{id}", boardA, ticketId))
                .andExpect(status().isOk());
    }

    private long createTicket(UUID boardId, String title) throws Exception {
        TicketCreateModel create = new TicketCreateModel();
        create.setTitle(title);

        String response = mockMvc.perform(post("/boards/{boardId}/tickets", boardId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
