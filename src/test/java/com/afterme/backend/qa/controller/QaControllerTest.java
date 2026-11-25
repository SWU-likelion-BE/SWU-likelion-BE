package com.afterme.backend.qa.controller;

import com.afterme.backend.global.security.*;
import com.afterme.backend.qa.domain.QaStatus;
import com.afterme.backend.qa.dto.QaDtos.*;
import com.afterme.backend.qa.service.QaService;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import org.junit.jupiter.api.*;
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

@WebMvcTest(QaController.class)
@Import({SecurityConfig.class, JwtConfig.class, TokenProvider.class, SecurityErrorResponder.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes!!", "jwt.issuer=afterme",
        "jwt.access-token-ttl=30m", "jwt.refresh-token-ttl=14d"
})
class QaControllerTest {
    static final String PATH = "/api/handovers/1/questions";
    static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");
    static final Question QUESTION = new Question(5L, 1L, 3L, 4L, 2L, "Question", QaStatus.OPEN, 1, 0, NOW, NOW);
    @Autowired MockMvc mvc;
    @Autowired TokenProvider tokens;
    @MockitoBean QaService service;
    @MockitoBean UserRepository users;

    String token() {
        given(users.findById(2L)).willReturn(Optional.of(User.create("qa@example.test", "hash", "QA")));
        return "Bearer " + tokens.createAccessToken(2L, Instant.now());
    }

    @Test void authenticationIsRequired() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test void createUsesAuthenticatedAuthorAndReturns201Data() throws Exception {
        given(service.create(2L, 1L, new CreateQuestion(4L, "Question"))).willReturn(QUESTION);
        mvc.perform(post(PATH).header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionId\":4,\"content\":\"Question\",\"authorId\":999}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.threadId").value(5))
                .andExpect(jsonPath("$.data.authorId").value(2));
        verify(service).create(2L, 1L, new CreateQuestion(4L, "Question"));
    }

    @Test void blankQuestionIsRejectedBeforeService() throws Exception {
        mvc.perform(post(PATH).header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionId\":4,\"content\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(service);
    }

    @Test void editRequiresRevision() throws Exception {
        mvc.perform(patch(PATH + "/5").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Changed\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(service);
    }

    @Test void listAndDetailUseDataEnvelope() throws Exception {
        given(service.list(2L, 1L, QaStatus.OPEN, 0, 20)).willReturn(new QuestionPage(List.of(QUESTION), 0, 20, 1, 1));
        given(service.detail(2L, 1L, 5L)).willReturn(new Detail(QUESTION, List.of()));
        String authorization = token();
        mvc.perform(get(PATH).param("status", "OPEN").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("OPEN"));
        mvc.perform(get(PATH + "/5").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.question.threadId").value(5))
                .andExpect(jsonPath("$.data.replies").isArray());
    }

    @Test void replyReturns201AndRequiresQuestionRevision() throws Exception {
        given(service.reply(2L, 1L, 5L, new CreateReply(1, "Answer")))
                .willReturn(new Reply(6L, 2L, "Answer", 1, "Question", NOW));
        String authorization = token();
        mvc.perform(post(PATH + "/5/replies").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionRevision\":1,\"content\":\"Answer\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.replyId").value(6));
        mvc.perform(post(PATH + "/5/replies").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionRevision\":0,\"content\":\"Answer\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
