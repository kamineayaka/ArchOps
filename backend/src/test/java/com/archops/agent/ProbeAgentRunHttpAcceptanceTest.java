package com.archops.agent;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.support.TestCredentialEncryptionKey;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Host agent pulls probe tasks, runs them with an injected command runner, and posts
 * structured results on the existing heartbeat ingest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(
        type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
        provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY,
        refresh = AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_CLASS
)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        TestCredentialEncryptionKey.SPRING_PROPERTY
})
class ProbeAgentRunHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String COMMAND = "printf '{\"up\":true}'";
    private static final String EQUAL_STDOUT = "{\"up\": true}";
    private static final String UNEQUAL_STDOUT = "{\"up\": false}";

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readableEqualCommandOutputIsNormalWithoutConflict() throws Exception {
        String hostId = createHost("probe-run-a-host");
        String taskId = createTask(hostId, COMMAND, 60);

        JsonNode posted = runAgent(
                "agent-probe-run-a",
                hostId,
                outcome(COMMAND, EQUAL_STDOUT, "", 0, false));

        JsonNode result = posted.path("probeResults").path(0);
        assertEquals(taskId, result.path("taskId").asText());
        assertEquals(true, result.path("readable").asBoolean());
        assertEquals(true, result.path("value").path("up").asBoolean());
        assertEquals(EQUAL_STDOUT, result.path("evidence").asText());
        assertFalse(result.has("judgment"));
        assertFalse(result.has("kind"));
        assertFalse(result.has("aiSuggestion"));
        assertFalse(posted.toString().contains("无法归类"));

        MvcResult got = mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.observedValue.up", is(true)))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.evidence", is(EQUAL_STDOUT)))
                .andReturn();
        String responseBody = got.getResponse().getContentAsString();
        assertFalse(responseBody.contains("无法归类"));
        assertFalse(responseBody.contains("RULES_WITH_LLM"));
        assertFalse(responseBody.contains("aiSuggestion"));
    }

    @Test
    void readableUnequalCommandOutputOpensConflictWithRawEvidence() throws Exception {
        String hostId = createHost("probe-run-b-host");
        String taskId = createTask(hostId, COMMAND, 60);

        JsonNode posted = runAgent(
                "agent-probe-run-b",
                hostId,
                outcome(COMMAND, UNEQUAL_STDOUT, "", 0, false));

        JsonNode result = posted.path("probeResults").path(0);
        assertEquals(taskId, result.path("taskId").asText());
        assertEquals(true, result.path("readable").asBoolean());
        assertEquals(false, result.path("value").path("up").asBoolean());
        assertEquals(UNEQUAL_STDOUT, result.path("evidence").asText());
        assertFalse(result.has("judgment"));
        assertFalse(result.has("kind"));

        MvcResult task = mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("CONFLICT")))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.observedValue.up", is(false)))
                .andExpect(jsonPath("$.data.evidence", is(UNEQUAL_STDOUT)))
                .andExpect(jsonPath("$.data.conflictId", notNullValue()))
                .andReturn();
        String conflictId = objectMapper.readTree(task.getResponse().getContentAsString())
                .path("data").path("conflictId").asText();

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.evidence", is(UNEQUAL_STDOUT)))
                .andExpect(jsonPath("$.data.curatedStructured.up", is(true)))
                .andExpect(jsonPath("$.data.observedStructured.up", is(false)));

        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        MvcResult diagnosis = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source", is("RULES")))
                .andExpect(jsonPath("$.data.forks", hasSize(2)))
                .andExpect(jsonPath("$.data.forks[*].id", containsInAnyOrder(
                        "FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")))
                .andReturn();
        String diagnosisBody = diagnosis.getResponse().getContentAsString();
        assertFalse(diagnosisBody.contains("\"kind\""));
        assertFalse(diagnosisBody.contains("无法归类"));
        assertFalse(diagnosisBody.contains("RULES_WITH_LLM"));
    }

    @Test
    void unreadableCommandOutputIsHollowForThatTask() throws Exception {
        String hostId = createHost("probe-run-c-host");
        String taskId = createTask(hostId, COMMAND, 60);

        JsonNode posted = runAgent(
                "agent-probe-run-c",
                hostId,
                outcome(COMMAND, "", "command error", 1, false));

        JsonNode result = posted.path("probeResults").path(0);
        assertEquals(taskId, result.path("taskId").asText());
        assertEquals(false, result.path("readable").asBoolean());
        assertEquals("command error", result.path("evidence").asText());
        assertFalse(result.has("value"));
        assertFalse(result.has("judgment"));
        assertFalse(result.has("kind"));
        assertFalse(posted.toString().contains("无法归类"));

        MvcResult got = mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("HOLLOW")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)))
                .andExpect(jsonPath("$.data.observedValue", nullValue()))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.evidence", is("command error")))
                .andReturn();
        assertFalse(got.getResponse().getContentAsString().contains("\"kind\""));
        assertFalse(got.getResponse().getContentAsString().contains("无法归类"));
    }

    @Test
    void timedOutCommandOmitsTheReportAndStaysHollow() throws Exception {
        String hostId = createHost("probe-run-d-host");
        String taskId = createTask(hostId, COMMAND, 0);

        JsonNode posted = runAgent(
                "agent-probe-run-d",
                hostId,
                outcome(COMMAND, "", "", 0, true));

        assertEquals(0, posted.path("probeResults").size());
        assertFalse(posted.toString().contains(taskId));

        mockMvc.perform(post("/api/probe-tasks/scan-overdue")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hollowedTaskIds", hasItem(taskId)));

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("HOLLOW")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.evidence", nullValue()))
                .andExpect(jsonPath("$.data.observedValue", nullValue()));
    }

    @Test
    void oneTimedOutTaskDoesNotHollowTheEqualTask() throws Exception {
        String hostId = createHost("probe-run-e-host");
        String missedCommand = "printf '{\"up\":true}'";
        String equalCommand = "printf '{\"up\":true}' # equal";
        String missedTask = createTask(hostId, missedCommand, 0);
        String equalTask = createTask(hostId, equalCommand, 60);

        JsonNode posted = runAgent(
                "agent-probe-run-e",
                hostId,
                outcomes(
                        outcome(missedCommand, "", "", 0, true),
                        outcome(equalCommand, EQUAL_STDOUT, "", 0, false)));

        assertEquals(1, posted.path("probeResults").size());
        assertEquals(equalTask, posted.path("probeResults").path(0).path("taskId").asText());
        assertEquals(true, posted.path("probeResults").path(0).path("readable").asBoolean());
        assertFalse(posted.toString().contains(missedTask));

        mockMvc.perform(post("/api/probe-tasks/scan-overdue")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hollowedTaskIds", hasItem(missedTask)))
                .andExpect(jsonPath("$.data.hollowedTaskIds", org.hamcrest.Matchers.not(hasItem(equalTask))));

        mockMvc.perform(get("/api/probe-tasks/{id}", missedTask)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("HOLLOW")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.evidence", nullValue()));

        mockMvc.perform(get("/api/probe-tasks/{id}", equalTask)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.observedValue.up", is(true)))
                .andExpect(jsonPath("$.data.conflictId", nullValue()));
    }

    @Test
    void heartbeatProcessRunsThePulledCommandAndPostsIt() throws Exception {
        String hostId = createHost("probe-run-h-host");
        String command = "printf '{\"up\":true}'";
        String taskId = createTask(hostId, command, 60);

        runHeartbeatProcess("agent-probe-run-h", hostId);

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.observedValue.up", is(true)))
                .andExpect(jsonPath("$.data.evidence", is("{\"up\":true}")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()));
    }

    @Test
    void secondHeartbeatProcessDoesNotSubmitResults() throws Exception {
        String hostId = createHost("probe-run-i-host");
        String taskId = createTask(hostId, "printf '{\"up\":true}'", 60);

        runHeartbeatProcess("agent-probe-run-i-a", hostId);
        runHeartbeatProcess("agent-probe-run-i-b", hostId);

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()));
    }

    @Test
    void emptyPullDoesNotSubmitProbeResults() throws Exception {
        runPythonUnittest("test_probe_cycle.NoTasksTests.test_no_tasks_posts_one_heartbeat_without_probe_results");
    }

    @Test
    void resultReportOmitsSnapshot() throws Exception {
        runPythonUnittest("test_probe_cycle.SnapshotReportTests.test_result_post_omits_snapshot");
    }

    @Test
    void taskIntervalDoesNotKillTheCommand() throws Exception {
        runPythonUnittest("test_probe_cycle.SubprocessRunnerTests.test_task_interval_does_not_kill_the_command");
    }

    @Test
    void unreachableControlPlaneIsAProbeCycleError() throws Exception {
        runPythonUnittest(
                "test_probe_cycle.UnreachableControlPlaneTests.test_unreachable_control_plane_is_a_probe_cycle_error");
    }

    private void runHeartbeatProcess(String agentId, String hostId) throws Exception {
        Path script = Path.of("..", "agent", "heartbeat.py").toAbsolutePath().normalize();
        Process process = new ProcessBuilder(
                "python3",
                script.toString(),
                "--control-plane",
                "http://127.0.0.1:" + port,
                "--agent-id",
                agentId,
                "--host-id",
                hostId,
                "--interval",
                "0")
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished || process.exitValue() != 0) {
            fail("heartbeat exit " + (finished ? process.exitValue() : "timeout")
                    + " stdout=" + stdout + " stderr=" + stderr);
        }
    }

    private void runPythonUnittest(String testName) throws Exception {
        Process process = new ProcessBuilder("python3", "-m", "unittest", testName)
                .directory(Path.of("..", "agent").toAbsolutePath().normalize().toFile())
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished || process.exitValue() != 0) {
            fail(testName + " exit " + (finished ? process.exitValue() : "timeout")
                    + " stdout=" + stdout + " stderr=" + stderr);
        }
    }

    private JsonNode runAgent(String agentId, String hostId, ObjectNode outcomes) throws Exception {
        Path script = Path.of("..", "agent", "probe_cycle.py").toAbsolutePath().normalize();
        Process process = new ProcessBuilder(
                "python3",
                script.toString(),
                "--control-plane",
                "http://127.0.0.1:" + port,
                "--agent-id",
                agentId,
                "--host-id",
                hostId)
                .start();
        process.getOutputStream().write(objectMapper.writeValueAsBytes(outcomes));
        process.getOutputStream().close();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished || process.exitValue() != 0) {
            fail("agent exit " + (finished ? process.exitValue() : "timeout")
                    + " stdout=" + stdout + " stderr=" + stderr);
        }
        return objectMapper.readTree(stdout);
    }

    private ObjectNode outcome(String command, String stdout, String stderr, int exitCode, boolean timedOut) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode outcome = root.putObject("outcomesByCommand").putObject(command);
        outcome.put("stdout", stdout);
        outcome.put("stderr", stderr);
        outcome.put("exitCode", exitCode);
        outcome.put("timedOut", timedOut);
        return root;
    }

    private ObjectNode outcomes(ObjectNode... parts) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode merged = root.putObject("outcomesByCommand");
        for (ObjectNode part : parts) {
            part.path("outcomesByCommand").fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
        }
        return root;
    }

    private String createHost(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/curated/hosts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String createTask(String hostId, String command, int intervalSeconds) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("hostId", hostId);
        request.put("command", command);
        request.put("intervalSeconds", intervalSeconds);
        request.putObject("curatedExpected").put("up", true);
        MvcResult result = mockMvc.perform(post("/api/probe-tasks")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }
}
