package com.afterme.backend.learning;

import com.afterme.backend.checklist.service.ChecklistService;
import com.afterme.backend.completion.dto.CompletionDtos.*;
import com.afterme.backend.completion.port.CompletenessPolicy;
import com.afterme.backend.completion.service.*;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.learning.port.LearningDocumentReader;
import com.afterme.backend.learning.port.LearningDocumentReader.*;
import com.afterme.backend.qa.dto.QaDtos.CreateQuestion;
import com.afterme.backend.qa.dto.QaDtos.CreateReply;
import com.afterme.backend.qa.port.PublishedSectionReader;
import com.afterme.backend.qa.service.QaService;
import com.afterme.backend.quiz.domain.*;
import com.afterme.backend.quiz.dto.QuizDtos.*;
import com.afterme.backend.quiz.repository.QuizSetRepository;
import com.afterme.backend.quiz.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Transactional
class LearningWorkflowIntegrationTest {
    @Autowired CompletionService confirmations;
    @Autowired CompletionWorkflowService workflow;
    @Autowired ReadinessService readiness;
    @Autowired ChecklistService checklist;
    @Autowired QuizService quiz;
    @Autowired QuizGenerationService generation;
    @Autowired QuizSetRepository quizzes;
    @Autowired QaService qa;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean LearningDocumentReader documents;
    @MockitoBean CompletenessPolicy policy;
    @MockitoBean PublishedSectionReader sections;
    Long predecessor, successor, project;

    @BeforeEach void fixture() {
        predecessor = user("Predecessor"); successor = user("Successor");
        Long organization = jdbc.queryForObject("insert into organization(owner_id) values (?) returning id", Long.class, predecessor);
        project = jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Learning project', 'Role', current_date + 1) returning id
                """, Long.class, organization);
        participant(organization, predecessor, "ADMIN", "PREDECESSOR");
        participant(organization, successor, "MEMBER", "SUCCESSOR");
        given(documents.requirePublishedVersion(project, 10L)).willReturn(version("Original evidence"));
        given(documents.requirePublishedChecklistItem(project, 20L, 10L, 30L)).willReturn(new ChecklistItem(project, 20L, 10L, 30L));
        given(documents.evidence(eq(project), anySet())).willReturn(new Evidence(true, 1, 0, 1, 1,
                Set.of(10L), Set.of(successor), List.of(new ChecklistItem(project, 20L, 10L, 30L))));
        given(documents.pageViews(eq(project), eq(Set.of(10L)), anySet()))
                .willReturn(PageViews.fromProgress(Map.of(successor, new PageCount(5, 5))));
        given(documents.scoringChecklistItems(project, Set.of(10L)))
                .willReturn(Optional.of(List.of(new ChecklistItem(project, 20L, 10L, 30L))));
        given(sections.requirePublishedSection(project, 11L)).willReturn(new PublishedSectionReader.SectionReference(project, 10L, 11L));
        // Test-only formula. Production deliberately has no agreed scoring policy yet.
        given(policy.score(any())).willReturn(OptionalInt.of(95));
    }

    @Test void approvalWritesProjectAndAuditAtomicallyAndIsIdempotent() {
        learningDone(); identitiesDone();
        assertThat(readiness.get(successor, project).eligible()).isTrue();
        assertThat(workflow.request(predecessor, project).status()).isEqualTo("REQUESTED");
        workflow.request(predecessor, project);
        assertThat(workflow.state(successor, project).approvalRequirementsMet()).isTrue();
        assertThat(workflow.approve(predecessor, project).status()).isEqualTo("APPROVED");
        workflow.approve(predecessor, project);
        em.flush(); em.clear();
        var state = workflow.state(successor, project);
        assertThat(state.projectStatus()).isEqualTo("COMPLETED");
        assertThat(state.completedAt()).isNotNull();
        assertThat(state.approval().approvedBy()).isEqualTo(predecessor);
        assertThat(state.requestRequirementsMet()).isFalse();
        assertThat(jdbc.queryForObject("select completeness from handover_project where id = ?", Integer.class, project)).isEqualTo(95);
        assertThat(jdbc.queryForObject("select approved_by from handover_project where id = ?", Long.class, project)).isEqualTo(predecessor);
        assertThat(jdbc.queryForObject("select count(*) from completion_audit where project_id = ?", Long.class, project)).isEqualTo(2);
    }

    @Test void finalApprovalRechecksNewUnansweredQuestionAfterRequest() {
        learningDone(); identitiesDone();
        workflow.request(predecessor, project);
        qa.create(successor, project, new CreateQuestion(11L, "New question"));
        assertThat(readiness.get(successor, project).unmet()).extracting(ReadinessService.Requirement::code).contains("UNANSWERED_QUESTIONS");
        error(() -> workflow.approve(predecessor, project), ErrorCode.CONFLICT);
        assertNotApproved();
    }

    @Test void finalApprovalRechecksChangedMinimum() {
        learningDone(); identitiesDone();
        workflow.request(predecessor, project);
        confirmations.updateCriteria(predecessor, project, new UpdateCriteria(100, null, 0L));
        assertThat(workflow.state(successor, project).predecessorConfirmed()).isTrue();
        error(() -> workflow.approve(predecessor, project), ErrorCode.CONFLICT);
        assertNotApproved();
    }

    @Test void approvalRequiresBothConfirmations() {
        learningDone();
        workflow.request(predecessor, project);
        confirmations.confirm(predecessor, project, new Confirm(ParticipantRole.PREDECESSOR));
        error(() -> workflow.approve(predecessor, project), ErrorCode.CONFLICT);
        assertNotApproved();
    }

    @Test void successorCannotApproveOrChangePredecessorChecklist() {
        error(() -> workflow.approve(successor, project), ErrorCode.FORBIDDEN);
        error(() -> checklist.change(predecessor, project, 20L, 10L, 30L, true), ErrorCode.FORBIDDEN);
    }

    @Test void unknownFormulaDoesNotTreatStoredProjectCompletenessAsVerifiedScore() {
        learningDone();
        given(policy.score(any())).willReturn(OptionalInt.empty());
        jdbc.update("update handover_project set completeness = 100 where id = ?", project);
        var evaluation = readiness.get(successor, project);
        assertThat(evaluation.score()).isNull();
        assertThat(evaluation.evaluated()).isFalse();
        assertThat(evaluation.eligible()).isFalse();
        error(() -> workflow.request(predecessor, project), ErrorCode.CONFLICT);
        assertNotApproved();
    }

    @Test void missingDocumentsReportUnavailabilityInsteadOfZeroScore() {
        given(documents.evidence(eq(project), anySet())).willReturn(Evidence.unavailable());
        var result = readiness.get(successor, project);
        assertThat(result.score()).isNull();
        assertThat(result.unmet()).extracting(ReadinessService.Requirement::code).contains("DOCUMENT_DATA_UNAVAILABLE");
        assertThat(result.eligible()).isFalse();
    }

    @Test void checklistPutIsIdempotentAndCanBeUncompleted() {
        var first = checklist.change(successor, project, 20L, 10L, 30L, true);
        var again = checklist.change(successor, project, 20L, 10L, 30L, true);
        assertThat(again.updatedAt()).isEqualTo(first.updatedAt());
        assertThat(checklist.change(successor, project, 20L, 10L, 30L, false).completed()).isFalse();
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("select count(*) from checklist_completion where project_id = ?", Long.class, project)).isEqualTo(1);
        assertThat(readiness.get(successor, project).unmet()).extracting(ReadinessService.Requirement::code).contains("REQUIRED_CHECKLIST_INCOMPLETE");
    }

    @Test void checklistRejectsReferenceFromWrongVersion() {
        given(documents.requirePublishedChecklistItem(project, 20L, 99L, 30L)).willReturn(new ChecklistItem(project, 20L, 10L, 30L));
        error(() -> checklist.change(successor, project, 20L, 99L, 30L, true), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(jdbc.queryForObject("select count(*) from checklist_completion where project_id = ?", Long.class, project)).isZero();
    }

    @Test void generationPreservesInputsAndCompletesExactlyOnce() {
        var first = generation.request(predecessor, project, new QuizGenerationService.Request(10L, "request-1"));
        var duplicate = generation.request(predecessor, project, new QuizGenerationService.Request(10L, "request-1"));
        assertThat(duplicate.jobId()).isEqualTo(first.jobId());
        Long id = Long.valueOf(first.jobId().substring(5));
        given(documents.requirePublishedVersion(project, 10L)).willReturn(version("Updated evidence"));
        var input = generation.claim(id).orElseThrow();
        assertThat(input.sections().get(0).content()).isEqualTo("Original evidence");
        assertThat(generation.claim(id)).isEmpty();
        var output = new QuizGenerationService.Output(List.of(new QuizGenerationService.GeneratedQuestion(
                "Question", List.of("A", "B"), 0, "Original evidence", 11L)), "test-provider", "test-model", "v1");
        Long quizId = generation.complete(id, output);
        assertThat(generation.complete(id, output)).isEqualTo(quizId);
        em.flush(); em.clear();
        assertThat(generation.status(successor, project, first.jobId()).status()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select count(*) from quiz_set where project_id = ?", Long.class, project)).isEqualTo(1);
        assertThat(quizzes.findById(quizId).orElseThrow().getQuestions().get(0).getTopic()).isEqualTo("TOOLS");
    }

    @Test void invalidGeneratedSourceNeverCreatesQuizAndFailurePreservesInput() {
        var job = generation.request(predecessor, project, new QuizGenerationService.Request(10L, "request-2"));
        Long id = Long.valueOf(job.jobId().substring(5));
        generation.claim(id);
        var output = new QuizGenerationService.Output(List.of(new QuizGenerationService.GeneratedQuestion(
                "Question", List.of("A", "B"), 0, "Explanation", 999L)), "test", "model", "v1");
        error(() -> generation.complete(id, output), ErrorCode.INVALID_INPUT);
        generation.fail(id, "INVALID_AI_OUTPUT");
        em.flush(); em.clear();
        assertThat(generation.status(successor, project, job.jobId()).status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from quiz_set where project_id = ?", Long.class, project)).isZero();
        assertThat(jdbc.queryForObject("select content from quiz_generation_source where job_id = ?", String.class, id)).isEqualTo("Original evidence");
    }

    @Test void reusedGenerationKeyCannotChangeSourceVersion() {
        generation.request(predecessor, project, new QuizGenerationService.Request(10L, "same-key"));
        error(() -> generation.request(predecessor, project, new QuizGenerationService.Request(99L, "same-key")), ErrorCode.CONFLICT);
    }

    @Test void scoreAboveThresholdWithoutAnyQuestionCannotRequestCompletion() {
        learningDoneWithoutQuestion(); identitiesDone();
        var result = readiness.get(successor, project);
        assertThat(result.score()).isEqualTo(95);
        assertThat(result.unmet()).extracting(ReadinessService.Requirement::code).contains("QUESTION_REQUIRED");
        error(() -> workflow.request(predecessor, project), ErrorCode.CONFLICT);
        assertNotApproved();
    }

    @Test void askingOneQuestionStillRequiresPredecessorAnswer() {
        learningDoneWithoutQuestion();
        var question = qa.create(successor, project, new CreateQuestion(11L, "Required question"));
        assertThat(readiness.get(successor, project).unmet()).extracting(ReadinessService.Requirement::code)
                .contains("UNANSWERED_QUESTIONS").doesNotContain("QUESTION_REQUIRED");
        qa.reply(predecessor, project, question.threadId(), new CreateReply(1, "Answer"));
        assertThat(readiness.get(successor, project).eligible()).isTrue();
    }

    @Test void acknowledgementDoesNotReplaceAllPageViews() {
        learningDone();
        given(documents.pageViews(eq(project), anySet(), anySet())).willReturn(new PageViews(true, Set.of()));
        var result = readiness.get(successor, project);
        assertThat(result.unmet()).extracting(ReadinessService.Requirement::code)
                .contains("ALL_PAGES_NOT_VIEWED").doesNotContain("DOCUMENT_ACKNOWLEDGEMENT_REQUIRED");
        error(() -> workflow.request(predecessor, project), ErrorCode.CONFLICT);
    }

    @Test void approvalRechecksPageViewsAfterRequest() {
        learningDone(); identitiesDone();
        workflow.request(predecessor, project);
        given(documents.pageViews(eq(project), anySet(), anySet())).willReturn(new PageViews(true, Set.of()));
        error(() -> workflow.approve(predecessor, project), ErrorCode.CONFLICT);
        assertNotApproved();
    }

    @Test void missingPageViewAdapterFailsClosed() {
        learningDone();
        given(documents.pageViews(eq(project), anySet(), anySet())).willReturn(PageViews.unavailable());
        assertThat(readiness.get(successor, project).unmet()).extracting(ReadinessService.Requirement::code)
                .contains("PAGE_VIEW_DATA_UNAVAILABLE");
        error(() -> workflow.request(predecessor, project), ErrorCode.CONFLICT);
    }

    @Test void perSuccessorQuestionPolicyRequiresEachActiveSuccessorToAsk() {
        learningDone();
        Long other = user("Other successor");
        Long organization = jdbc.queryForObject("select organization_id from handover_project where id = ?", Long.class, project);
        participant(organization, other, "MEMBER", "SUCCESSOR");
        assertThat(readiness.get(successor, project).unmet())
                .contains(new ReadinessService.Requirement("QUESTION_REQUIRED", other, null))
                .doesNotContain(new ReadinessService.Requirement("QUESTION_REQUIRED", successor, null));
    }

    @Test void agreedPolicyCalculatesActualFourCategoryScore() {
        learningDone();
        given(policy.score(any())).willAnswer(invocation -> new EqualWeightCompletenessPolicy().score(invocation.getArgument(0)));
        given(documents.pageViews(eq(project), anySet(), anySet()))
                .willReturn(PageViews.fromProgress(Map.of(successor, new PageCount(5, 4))));
        var result = readiness.get(successor, project);
        assertThat(result.score()).isEqualTo(95); // (80 + 100 + 100 + 100) / 4
        assertThat(result.evidence().participantScores()).singleElement().satisfies(score -> {
            assertThat(score.userId()).isEqualTo(successor);
            assertThat(score.pageViewRate()).isEqualByComparingTo("80");
            assertThat(score.score()).isEqualTo(95);
        });
        assertThat(result.eligible()).isFalse(); // A high score never replaces the all-pages gate.
        assertThat(result.unmet()).extracting(ReadinessService.Requirement::code).contains("ALL_PAGES_NOT_VIEWED");
    }

    @Test void optionalChecklistItemsAffectScoreWithoutBecomingMandatory() {
        learningDone();
        given(policy.score(any())).willAnswer(invocation -> new EqualWeightCompletenessPolicy().score(invocation.getArgument(0)));
        given(documents.scoringChecklistItems(project, Set.of(10L))).willReturn(Optional.of(List.of(
                new ChecklistItem(project, 20L, 10L, 30L), new ChecklistItem(project, 20L, 10L, 31L))));
        var result = readiness.get(successor, project);
        assertThat(result.score()).isEqualTo(87);
        assertThat(result.evidence().participantScores().get(0).checklistRate()).isEqualByComparingTo("50");
        assertThat(result.eligible()).isTrue();
    }

    @Test void legacyCompletionOnlyViewsCannotInventPagePercentage() {
        learningDone();
        given(policy.score(any())).willAnswer(invocation -> new EqualWeightCompletenessPolicy().score(invocation.getArgument(0)));
        given(documents.pageViews(eq(project), anySet(), anySet())).willReturn(new PageViews(true, Set.of(successor)));
        assertThat(readiness.get(successor, project).score()).isNull();
    }

    @Test void multipleSuccessorsExposeIndividualScoresWithoutInventingProjectAggregation() {
        learningDone();
        Long other = user("Another successor");
        Long organization = jdbc.queryForObject("select organization_id from handover_project where id = ?", Long.class, project);
        participant(organization, other, "MEMBER", "SUCCESSOR");
        given(policy.score(any())).willAnswer(invocation -> new EqualWeightCompletenessPolicy().score(invocation.getArgument(0)));
        given(documents.pageViews(eq(project), anySet(), anySet())).willReturn(PageViews.fromProgress(Map.of(
                successor, new PageCount(5, 5), other, new PageCount(5, 5))));
        var result = readiness.get(successor, project);
        assertThat(result.score()).isNull();
        assertThat(result.evidence().participantScores()).extracting(EqualWeightCompletenessPolicy.ParticipantScore::score)
                .containsExactlyInAnyOrder(100, 25);
        assertThat(result.unmet()).contains(new ReadinessService.Requirement("QUESTION_REQUIRED", other, null));
        assertThat(result.eligible()).isFalse();
    }

    @Test void missingScoringPopulationCannotTreatRequiredItemsAsAllItems() {
        learningDone();
        given(policy.score(any())).willAnswer(invocation -> new EqualWeightCompletenessPolicy().score(invocation.getArgument(0)));
        given(documents.scoringChecklistItems(project, Set.of(10L))).willReturn(Optional.empty());
        assertThat(readiness.get(successor, project).score()).isNull();
    }

    void learningDone() {
        learningDoneWithoutQuestion();
        var question = qa.create(successor, project, new CreateQuestion(11L, "Required question"));
        qa.reply(predecessor, project, question.threadId(), new CreateReply(1, "Answer"));
    }
    void learningDoneWithoutQuestion() {
        var set = quizzes.saveAndFlush(QuizSet.ready(project, 10L, 70, List.of(QuizQuestion.multipleChoice(
                "Question", List.of("A", "B"), 0, "Evidence", 11L, "TOOLS"))));
        var started = quiz.start(successor, project, set.getId());
        quiz.submit(successor, project, set.getId(), started.attemptId(), new Submission(List.of(new Answer(set.getQuestions().get(0).getId(), 0))));
        checklist.change(successor, project, 20L, 10L, 30L, true);
    }
    void identitiesDone() {
        confirmations.confirm(predecessor, project, new Confirm(ParticipantRole.PREDECESSOR));
        confirmations.confirm(successor, project, new Confirm(ParticipantRole.SUCCESSOR));
    }
    PublishedVersion version(String text) { return new PublishedVersion(project, 20L, 10L, List.of(new Section(11L, "TOOLS", text))); }
    void assertNotApproved() {
        assertThat(jdbc.queryForObject("select status from handover_project where id = ?", String.class, project)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from completion_audit where project_id = ? and action = 'APPROVED'", Long.class, project)).isZero();
    }
    Long user(String name) {
        return jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', ?, current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test", name);
    }
    void participant(Long organization, Long userId, String organizationRole, String role) {
        Long member = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, ?, 'ACTIVE') returning id
                """, Long.class, organization, userId, organizationRole);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, ?)", project, member, role);
    }
    void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
}
