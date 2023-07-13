package com.camunda.consulting.zeebe_workaround_operation_batch.worker;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.ModifyProcessInstanceResponse;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.spring.client.annotation.JobWorker;

@Component
public class ModifyProcessInstanceWorker implements JobHandler {

  public static final String PROCESS_INSTANCE_TO_MODIFY_VAR = "processInstanceToModify";
  public static final String PROCESS_INSTANCE_TO_MODIFY_KEY_VAR = "processInstanceToModifyKey";
  public static final String TERMINATED_ELEMENT_INSTANCE_VAR = "terminatedElementInstance";
  public static final String ACTIVATED_ELEMENT_VAR = "activatedElement";
  public static final String MODIFICATION_RESULT_VAR = "modificationResult";

  private static final Logger LOG = LoggerFactory.getLogger(ModifyProcessInstanceWorker.class);

  ZeebeClient zeebeClient;

  @Autowired
  public ModifyProcessInstanceWorker(ZeebeClient zeebeClient) {
    super();
    this.zeebeClient = zeebeClient;
  }

  @JobWorker(type = "processInstanceModification", autoComplete = false)
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    LOG.info("Modifying process instance");

    Map<?, ?> modification = (Map<?, ?>) job.getVariablesAsMap().get(PROCESS_INSTANCE_TO_MODIFY_VAR);

    long processInstanceKey = (long) modification.get(PROCESS_INSTANCE_TO_MODIFY_KEY_VAR);
    String activatedElementId = (String) modification.get(ACTIVATED_ELEMENT_VAR);
    long terminatedElementInstanceKey = (long) modification.get(TERMINATED_ELEMENT_INSTANCE_VAR);

    LOG.info("modify {} from {} to {}", processInstanceKey, terminatedElementInstanceKey,
        activatedElementId);

    try {
      ModifyProcessInstanceResponse modifyProcessInstanceResponse =
          zeebeClient.newModifyProcessInstanceCommand(processInstanceKey)
              .terminateElement(terminatedElementInstanceKey).and()
              .activateElement(activatedElementId).send().join();

      LOG.info("Process instance {} modified: {}", modifyProcessInstanceResponse);
      client.newCompleteCommand(job)
          .variables(Map.of(MODIFICATION_RESULT_VAR, "completed: " + processInstanceKey)).send();
    } catch (Exception e) {
      LOG.info("Failed to execute migration: {}", e.getLocalizedMessage());
      client.newThrowErrorCommand(job).errorCode("modificationFailedError")
          .variables(Map.of(MODIFICATION_RESULT_VAR,
              "failed: " + processInstanceKey, "errorMessage", e.getLocalizedMessage()))
          .send();
    }
  }
}
