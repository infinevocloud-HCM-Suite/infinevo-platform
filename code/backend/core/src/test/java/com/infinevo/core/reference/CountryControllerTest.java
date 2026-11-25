package com.infinevo.core.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CountryControllerTest {

    @Test
    @DisplayName("Returns list of countries ordered by name")
    @SuppressWarnings("unchecked")
    void listsCountries() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<CountryResponse> mapper = invocation.getArgument(1);
            ResultSet rs1 = mock(ResultSet.class);
            when(rs1.getString("code")).thenReturn("IN");
            when(rs1.getString("name")).thenReturn("India");
            ResultSet rs2 = mock(ResultSet.class);
            when(rs2.getString("code")).thenReturn("US");
            when(rs2.getString("name")).thenReturn("United States");
            return List.of(mapper.mapRow(rs1, 0), mapper.mapRow(rs2, 1));
        });

        CountryController controller = new CountryController(jdbc);
        ResponseEntity<List<CountryResponse>> response = controller.list();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0)).isEqualTo(new CountryResponse("IN", "India"));
        assertThat(response.getBody().get(1)).isEqualTo(new CountryResponse("US", "United States"));
    }
}
