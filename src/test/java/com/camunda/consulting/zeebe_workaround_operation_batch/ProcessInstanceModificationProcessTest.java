package com.camunda.consulting.zeebe_workaround_operation_batch;

import static com.camunda.consulting.zeebe_workaround_operation_batch.worker.ModifyProcessInstanceWorker.*;
import static io.camunda.zeebe.process.test.assertions.BpmnAssert.*;
import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;

@ZeebeProcessTest
public class ProcessInstanceModificationProcessTest {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessInstanceModificationProcessTest.class);

  private static final String PROCESS_INSTANCE_MODIFICATION_PROCESS =
      "ProcessInstanceModificationProcess";

  public ZeebeClient client;
  public ZeebeTestEngine testEngine;
  public RecordStream recordStream;


  @BeforeEach
  public void deployment() {
    DeploymentEvent deploymentEvent = client.newDeployResourceCommand()
        .addResourceFromClasspath("process-instance-modification-process.bpmn").send().join();
    assertThat(deploymentEvent)
        .containsProcessesByBpmnProcessId(PROCESS_INSTANCE_MODIFICATION_PROCESS);
  }

  @Test
  public void testHappyPath() throws InterruptedException, TimeoutException {
    var processInstanceToModify = Map.of(PROCESS_INSTANCE_TO_MODIFY_KEY_VAR, 1L,
        TERMINATED_ELEMENT_INSTANCE_VAR, 2L, ACTIVATED_ELEMENT_VAR, "myTask");
    ProcessInstanceEvent processInstanceEvent = startProcessInstance(processInstanceToModify);

    ActivatedJob activatedJob = client.newActivateJobsCommand()
        .jobType("processInstanceModification").maxJobsToActivate(1).send().join().getJobs().get(0);
    assertThat(activatedJob).extractingVariables().containsKey(PROCESS_INSTANCE_TO_MODIFY_VAR);
    
    Map<?, ?> modifications =
        (Map<?, ?>) activatedJob.getVariablesAsMap().get(PROCESS_INSTANCE_TO_MODIFY_VAR);
    Object processInstanceToModifyKey = modifications.get(PROCESS_INSTANCE_TO_MODIFY_KEY_VAR);
    assertThat(processInstanceToModifyKey).isEqualTo(1);
    assertThat(modifications.get(TERMINATED_ELEMENT_INSTANCE_VAR)).isEqualTo(2);
    assertThat(modifications.get(ACTIVATED_ELEMENT_VAR)).isEqualTo("myTask");

    client.newCompleteCommand(activatedJob)
        .variables(Map.of(MODIFICATION_RESULT_VAR, "completed: " + processInstanceToModifyKey))
        .send().join();

    testEngine.waitForIdleState(Duration.ofSeconds(1));
    assertThat(processInstanceEvent).isCompleted().hasVariableWithValue("results",
        List.of("completed: 1"));
  }

  @Test
  @Disabled("throw error RPC doesn't transfer variable to Error event in this setup. Works in production.")
  public void modificationError() throws InterruptedException, TimeoutException {
    var processInstanceToModify = Map.of(PROCESS_INSTANCE_TO_MODIFY_KEY_VAR, 3L,
        TERMINATED_ELEMENT_INSTANCE_VAR, 4L, ACTIVATED_ELEMENT_VAR, "myTaskNotFound");

    ProcessInstanceEvent processInstanceEvent = startProcessInstance(processInstanceToModify);

    ActivatedJob activatedJob = client.newActivateJobsCommand()
        .jobType("processInstanceModification").maxJobsToActivate(1).send().join().getJobs().get(0);

    Object processInstanceToModifyKey =
        ((Map<?, ?>) activatedJob.getVariablesAsMap().get(PROCESS_INSTANCE_TO_MODIFY_VAR))
            .get(PROCESS_INSTANCE_TO_MODIFY_KEY_VAR);
    LOG.info("key: {}", processInstanceToModifyKey);
    client.newThrowErrorCommand(activatedJob).errorCode("modificationFailedError")
        .variables(Map.of(MODIFICATION_RESULT_VAR, "failed: " + processInstanceToModifyKey,
            "errorMessage", "Message from the stack trace"))
        .send().join();

    testEngine.waitForIdleState(Duration.ofSeconds(1));
    assertThat(processInstanceEvent).isCompleted().hasVariableWithValue("results",
        List.of("failed: 3"));
  }

  public ProcessInstanceEvent startProcessInstance(Map<?, ?> processInstanceToModify) {
    Map<String, Object> inputVars = Map.of("modifications", List.of(processInstanceToModify));

    ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_INSTANCE_MODIFICATION_PROCESS)
            .latestVersion().variables(inputVars).send().join();
    assertThat(processInstanceEvent).isActive().hasNoIncidents()
        .isWaitingAtElements("ApplyModificationTask");
    return processInstanceEvent;
  }

}
