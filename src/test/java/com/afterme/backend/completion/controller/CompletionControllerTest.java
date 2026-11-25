package com.afterme.backend.completion.controller;

import com.afterme.backend.completion.dto.CompletionDtos.*;
import com.afterme.backend.completion.service.CompletionService;
import com.afterme.backend.completion.service.CompletionWorkflowService;
import com.afterme.backend.completion.service.ReadinessService;
import com.afterme.backend.global.security.*;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompletionController.class)
@Import({SecurityConfig.class, JwtConfig.class, TokenProvider.class, SecurityErrorResponder.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes!!", "jwt.issuer=afterme",
        "jwt.access-token-ttl=30m", "jwt.refresh-token-ttl=14d"
})
class CompletionControllerTest {
    static final String PATH = "/api/handovers/1/completion";
    @Autowired MockMvc mvc;
    @Autowired TokenProvider tokens;
    @MockitoBean CompletionService service;
    @MockitoBean CompletionWorkflowService workflow;
    @MockitoBean UserRepository users;

    String token() {
        given(users.findById(42L)).willReturn(Optional.of(User.create("user@example.test", "hash", "Name")));
        return "Bearer " + tokens.createAccessToken(42L, Instant.now());
    }

    @Test void authenticationIsRequired() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test void partialUpdateUsesTokenIdentityAndReturnsCriteria() throws Exception {
        given(service.updateCriteria(42L, 1L, new UpdateCriteria(null, 85, 0L)))
                .willReturn(new Criteria(80, 85, 1, 42L, Instant.now()));
        mvc.perform(patch(PATH + "/criteria").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quizPassingScore\":85,\"revision\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.quizPassingScore").value(85))
                .andExpect(jsonPath("$.data.minimumCompleteness").value(80));
        verify(service).updateCriteria(42L, 1L, new UpdateCriteria(null, 85, 0L));
    }

    @Test void invalidBoundsAndMissingRevisionAreRejectedBeforeService() throws Exception {
        String authorization = token();
        for (String body : List.of("{\"minimumCompleteness\":79,\"quizPassingScore\":69,\"revision\":0}",
                "{\"quizPassingScore\":101,\"revision\":0}", "{\"quizPassingScore\":80}")) {
            mvc.perform(patch(PATH + "/criteria").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        verifyNoInteractions(service);
    }

    @Test void confirmationRequiresOnlyAssignedRoleAndReturns200() throws Exception {
        given(service.confirm(42L, 1L, new Confirm(ParticipantRole.SUCCESSOR)))
                .willReturn(new Confirmation(5L, 7L, ParticipantRole.SUCCESSOR, 0, Instant.now()));
        mvc.perform(put(PATH + "/confirmations/me").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"SUCCESSOR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.memberId").value(7));
        verify(service).confirm(42L, 1L, new Confirm(ParticipantRole.SUCCESSOR));
    }

    @Test void statusSeparatesConfirmationFromEligibility() throws Exception {
        given(workflow.state(42L, 1L)).willReturn(new CompletionWorkflowService.Details(1L, "DRAFT", null,
                new Criteria(80, 70, 0, null, null), List.of(), false, false, false, null,
                new CompletionWorkflowService.Approval("NOT_REQUESTED", null, null, null, null), false, false));
        mvc.perform(get(PATH).header("Authorization", token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.eligibilityEvaluated").value(false))
                .andExpect(jsonPath("$.data.criteria.revision").value(0));
    }

    @Test void completionRequestAndApprovalUseAuthenticatedIdentity() throws Exception {
        given(workflow.request(42L, 1L)).willReturn(new CompletionWorkflowService.Approval("REQUESTED", 42L, Instant.now(), null, null));
        given(workflow.approve(42L, 1L)).willReturn(new CompletionWorkflowService.Approval("APPROVED", 42L, Instant.now(), 42L, Instant.now()));
        String authorization = token();
        mvc.perform(post(PATH).header("Authorization", authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));
        mvc.perform(post(PATH + "/approval").header("Authorization", authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }
}
