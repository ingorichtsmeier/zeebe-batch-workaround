package com.camunda.consulting.zeebe_workaround_operation_batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.camunda.zeebe.spring.client.annotation.Deployment;

@SpringBootApplication
@Deployment(resources = "classpath:**/*.bpmn")
public class ZeebeWorkaroundBatchOperationApplication {

  public static void main(String[] args) {
    SpringApplication.run(ZeebeWorkaroundBatchOperationApplication.class, args);
  }

}
