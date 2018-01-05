package io.holunda.example.cmmn.exitcriterion;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.mock.MockExpressionManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.cmmn.CmmnAwareTests.*;

@RunWith(MockitoJUnitRunner.class)
public class ProcessTerminateTest {

    private ProcessEngineConfiguration processEngineConfiguration = new StandaloneInMemProcessEngineConfiguration() {{
        this.jobExecutorActivate = false;
        this.expressionManager = new MockExpressionManager();
        this.databaseSchemaUpdate = ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_DROP_CREATE;
        this.isMetricsEnabled = false;
        this.historyLevel = HistoryLevel.HISTORY_LEVEL_FULL;
    }};

    @Rule
    public ProcessEngineRule rule = new ProcessEngineRule(processEngineConfiguration.buildProcessEngine());

    @Test
    @Deployment(resources = {"case_with_process_task.cmmn", "process_wait_for_something.bpmn"})
    public void shouldTerminateProcess() {

        CaseInstance caseInstance = caseService().createCaseInstanceByKey("case_wirh_process_task");

        // both activities should be active
        assertThat(caseInstance).humanTask("human_task_cancel_process").isActive();
        assertThat(caseInstance).processTask("process_task_wait_for_something").isActive();

        // process instance should be active, too
        ProcessInstance processInstance = runtimeService().createProcessInstanceQuery().processDefinitionKey("process_wait_for_something").singleResult();
        assertThat(processInstance).isActive();

        // complete the 'cancel' task
        complete(caseExecution("human_task_cancel_process", caseInstance));

        // both activities should be ended
        assertThat(historyService().createHistoricCaseActivityInstanceQuery().caseActivityId("human_task_cancel_process").singleResult().isCompleted()).isTrue();
        assertThat(historyService().createHistoricCaseActivityInstanceQuery().caseActivityId("process_task_wait_for_something").singleResult().isTerminated()).isTrue();

        // task and process instance should be ended, too
        assertThat(historyService().createHistoricTaskInstanceQuery().taskDefinitionKey("human_task_cancel_process").singleResult().getDeleteReason()).isEqualTo("completed");
        assertThat(processInstance).isEnded(); // <-- this fails, because the process seems not to be terminated :(
    }

}
