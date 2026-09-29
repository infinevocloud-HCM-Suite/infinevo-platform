package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W-23.1 spec section 7 — 10,000 rows export through the whole path: database stream, streaming
 * workbook, document store, signed link.
 *
 * <p>That memory stays bounded is asserted where it can be asserted deterministically,
 * {@code XlsxStreamingWriterTest}, by counting the rows the workbook holds after every row. This test
 * proves the path carries that size end to end and the file comes back whole.
 */
@SpringBootTest(classes = ReportTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            ReportTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ExportStreamingIT extends AbstractIntegrationTest {

    private static final int ROWS = 10_000;

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("10,000 employees export to xlsx and read back as 10,000 rows plus a header")
    void tenThousandRows() throws Exception {
        UUID tenant = ReportTestSchema.insertTenant("Bulk " + UUID.randomUUID());
        UUID admin = UUID.randomUUID();
        ReportTestSchema.insertMember(tenant, admin, "tenant-admin");
        ReportTestSchema.insertEmployees(tenant, ROWS);

        String body = "{\"definitionId\":\"" + ReportTestSchema.definitionId(tenant, "employees") + "\"}";
        JsonNode response = json.readTree(mvc.perform(post("/api/v1/exports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(t -> t.subject(admin.toString()).claim("tenant_id", tenant.toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(response.get("rowCount").asLong()).isEqualTo(ROWS);

        String url = response.get("url").asText();
        byte[] file = mvc.perform(
                        get(PublicEndpoints.DOCUMENT_DOWNLOAD).param("t", url.substring(url.indexOf("?t=") + 3)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (XSSFWorkbook read = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(read.getSheetAt(0).getLastRowNum()).isEqualTo(ROWS);
        }
    }
}
