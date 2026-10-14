package com.smartqueue.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.IntegrationTestBase;
import com.smartqueue.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end HTTP tests: authentication, role permissions, guest flow and error shape. */
@AutoConfigureMockMvc
class ApiSecurityIT extends IntegrationTestBase {

    private static final String PASSWORD = "Passw0rd!Passw0rd";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TestData data;

    private String login(String username) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void loginRejectsWrongPasswordWithGenericError() throws Exception {
        var fx = data.createQueue(null);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + fx.staff().getUsername() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"nobody-here\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        var fx = data.createQueue(null);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"" + fx.staff().getUsername() + "\",\"password\":\"wrong\"}"));
        }
        // Even the correct password is refused while locked
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + fx.staff().getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/admin/queues"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/staff/queues")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotUseAdminApisAndCustomersCannotUseStaffApis() throws Exception {
        var fx = data.createQueue(null);
        String staffToken = login(fx.staff().getUsername());
        mvc.perform(get("/api/admin/queues").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        var customer = data.user("cust-" + System.nanoTime(), com.smartqueue.entity.Role.CUSTOMER, java.util.Set.of());
        String customerToken = login(customer.getUsername());
        mvc.perform(get("/api/staff/queues").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/queues/" + fx.queue().getId() + "/next").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAdminApis() throws Exception {
        var fx = data.createQueue(null);
        String token = login(fx.admin().getUsername());
        mvc.perform(get("/api/admin/queues").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokensToday").isNumber());
    }

    @Test
    void guestJoinsThenStaffCallsAndGuestSeesTheUpdate() throws Exception {
        var fx = data.createQueue(null);

        MvcResult joined = mvc.perform(post("/api/queues/" + fx.queue().getId() + "/join")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token.tokenNumber").value("A001"))
                .andExpect(jsonPath("$.token.status").value("WAITING"))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn();
        JsonNode body = json.readTree(joined.getResponse().getContentAsString());
        String reference = body.get("token").get("reference").asText();
        String sessionId = body.get("sessionId").asText();

        // Same guest session cannot join the same queue twice
        mvc.perform(post("/api/queues/" + fx.queue().getId() + "/join").header("X-Session-Id", sessionId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACTIVE_TOKEN"));

        String staffToken = login(fx.staff().getUsername());
        mvc.perform(post("/api/queues/" + fx.queue().getId() + "/next").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"counterId\":\"" + fx.counter1().getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"));

        mvc.perform(get("/api/tokens/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"))
                .andExpect(jsonPath("$.counterName").value("Counter 1"))
                .andExpect(jsonPath("$.message").value("PLEASE PROCEED TO COUNTER 1"));
    }

    @Test
    void errorsNeverLeakInternals() throws Exception {
        mvc.perform(get("/api/tokens/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_FOUND"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());

        MvcResult bad = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(bad.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("at com.");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"fullName\":\"A\",\"password\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/public/organizations"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void queueQrCodeIsAPng() throws Exception {
        var fx = data.createQueue(null);
        MvcResult res = mvc.perform(get("/api/public/queues/" + fx.queue().getPublicRef() + "/qr"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn();
        byte[] png = res.getResponse().getContentAsByteArray();
        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }
}
