package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** W-114.4 spec, "Masking test table": every row, plus the key rules and the numeric-node case. */
class AiMaskingFilterTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFzaGEiLCJpYXQiOjE1MTYyMzkwMjJ9"
            + ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    private final AiMaskingFilter filter = new AiMaskingFilter();
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "My PAN is ABCDE1234F | My PAN is [PAN]",
                "abcde1234f | [PAN]",
                "Aadhaar 2345 6789 0123 | Aadhaar [AADHAAR]",
                "234567890123 | [AADHAAR]",
                "Aadhaar 2345 6789 0124 | Aadhaar [AADHAAR]",
                "Aadhaar 2345 6789 0125 | Aadhaar [AADHAAR]",
                "'A/c 50100123456789, HDFC0001234' | 'A/c [NUMBER], [IFSC]'",
                "'Net pay 125000.00 for 2026-10, 4.5 days leave' | 'Net pay 125000.00 for 2026-10, 4.5 days leave'"
            })
    @DisplayName("text rows of the masking table")
    void textRows(String input, String expected) {
        assertThat(filter.mask(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Bearer and a full token becomes [TOKEN]")
    void bearerToken() {
        assertThat(filter.mask("Bearer " + JWT)).isEqualTo("[TOKEN]");
    }

    @Test
    @DisplayName("a bare JWT becomes [TOKEN], not [NUMBER] pieces")
    void bareJwt() {
        assertThat(filter.mask("token " + JWT + " end")).isEqualTo("token [TOKEN] end");
    }

    @Test
    @DisplayName("{ pan, netPay }: the PAN key is hidden, the amount passes")
    void panKey() throws Exception {
        assertThat(filter.mask(json.readTree("{ \"pan\": \"ABCDE1234F\", \"netPay\": \"45000.00\" }")))
                .isEqualTo(json.readTree("{ \"pan\": \"[hidden]\", \"netPay\": \"45000.00\" }"));
    }

    @Test
    @DisplayName("{ bank_account_number, name }: the account is hidden, the name passes")
    void bankAccountKey() throws Exception {
        assertThat(filter.mask(json.readTree("{ \"bank_account_number\": \"12345\", \"name\": \"Asha\" }")))
                .isEqualTo(json.readTree("{ \"bank_account_number\": \"[hidden]\", \"name\": \"Asha\" }"));
    }

    @Test
    @DisplayName("{ company, quantity, span } is unchanged: pan and uan match only as a whole key")
    void shortNamesInsideWords() throws Exception {
        JsonNode input = json.readTree("{ \"company\": \"Acme\", \"quantity\": \"3\", \"span\": \"5\" }");
        assertThat(filter.mask(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "pan",
                "PAN",
                "panNumber",
                "pan_no",
                "uan",
                "uanNumber",
                "UAN-No",
                "aadhaarNumber",
                "aadhar",
                "accountNumber",
                "bankAccount",
                "ifscCode",
                "esi_number",
                "passportNo",
                "password",
                "clientSecret",
                "accessToken",
                "api-key",
                "account_no",
                "accountNo",
                "ac_no",
                "AcNo",
                "esic_no",
                "esicNumber",
                "ESIC",
                "pf_number",
                "pfNumber",
                "epfNumber"
            })
    @DisplayName("every sensitive key is hidden, nested in arrays too")
    void sensitiveKeys(String key) throws Exception {
        JsonNode input = json.createObjectNode()
                .set("rows", json.createArrayNode().add(json.createObjectNode().put(key, "x")));
        assertThat(filter.mask(input).path("rows").path(0).path(key).asText()).isEqualTo("[hidden]");
    }

    @Test
    @DisplayName("{ account_no, ac_no, esic_no, pf_number }: every value is hidden (added 2026-11-23)")
    void newKeys() throws Exception {
        assertThat(filter.mask(json.readTree("{ \"account_no\": \"12345\", \"ac_no\": \"678\", \"esic_no\": \"31001\","
                        + " \"pf_number\": \"KN/1/2\" }")))
                .isEqualTo(json.readTree("{ \"account_no\": \"[hidden]\", \"ac_no\": \"[hidden]\","
                        + " \"esic_no\": \"[hidden]\", \"pf_number\": \"[hidden]\" }"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "basic",
                "basicSalary",
                "basic_salary",
                "esi_amount",
                "esiEmployee",
                "pf_amount",
                "pfDeduction",
                "employer_pf",
                "accountType",
                "accounting_period",
                "bank_name",
                "designation",
                "description",
                "company",
                "quantity"
            })
    @DisplayName("ordinary keys pass, the four new key parts included (basic does not contain esic)")
    void ordinaryKeysPass(String key) {
        assertThat(AiMaskingFilter.isSensitiveKey(key)).isFalse();
    }

    @Test
    @DisplayName("pass 2 runs on JSON values too, a long JSON number included; the input is not changed")
    void patternsInsideJson() throws Exception {
        JsonNode input =
                json.readTree("{ \"note\": \"PAN ABCDE1234F\", \"ref\": 50100123456789, \"days\": 4.5, \"n\": 12 }");
        JsonNode out = filter.mask(input);
        assertThat(out.path("note").asText()).isEqualTo("PAN [PAN]");
        assertThat(out.path("ref").asText()).isEqualTo("[NUMBER]");
        assertThat(out.path("days").decimalValue()).isEqualByComparingTo("4.5");
        assertThat(out.path("n").asInt()).isEqualTo(12);
        assertThat(input.path("note").asText()).isEqualTo("PAN ABCDE1234F");
    }

    @Test
    @DisplayName("names, email, dates and times pass through")
    void ordinaryValuesPass() {
        String text = "Asha Rao, asha.rao@example.com, Engineering, Pune, 2026-11-23 09:30, balance 12.5";
        assertThat(filter.mask(text)).isEqualTo(text);
    }
}
