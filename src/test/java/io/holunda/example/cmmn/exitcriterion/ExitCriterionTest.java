package io.holunda.example.cmmn.exitcriterion;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.history.HistoricCaseActivityInstance;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.assertions.cmmn.CmmnAwareTests;
import org.camunda.bpm.engine.test.mock.MockExpressionManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static org.camunda.bpm.engine.test.assertions.cmmn.CmmnAwareTests.*;

@RunWith(MockitoJUnitRunner.class)
public class ExitCriterionTest {

    private ProcessEngineConfiguration processEngineConfiguration = new StandaloneInMemProcessEngineConfiguration() {{
        this.jobExecutorActivate = false;
        this.expressionManager = new MockExpressionManager();
        this.databaseSchemaUpdate = ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_DROP_CREATE;
        this.isMetricsEnabled = false;
    }};

    @Rule
    public ProcessEngineRule rule = new ProcessEngineRule(processEngineConfiguration.buildProcessEngine());

    @Test
    @Deployment(resources = "case_with_exit_criterion.cmmn")
    public void shouldTerminateOneTask() {

        CaseInstance caseInstance = caseService().createCaseInstanceByKey("case");

        assertThat(caseInstance).isActive();

        // Expecting Task_1 and Task_3 to be active, Task_2 to be available
        assertThat(caseInstance).humanTask("task_1").isActive();
        assertThat(caseInstance).humanTask("task_3").isActive();
        assertThat(caseInstance).humanTask("task_2").isAvailable();

        complete(CmmnAwareTests.caseExecution("task_1", caseInstance));

        // Expecting Task_1 to be completed
        assertThat(findHistoricCaseActivityInstance(caseInstance.getCaseInstanceId(), "task_1").get(0).isCompleted()).isTrue();

        // Expecting Task_2 now to be enabled AND available
        assertThat(findCaseExecutions(caseInstance.getCaseInstanceId(), "task_2").size()).isEqualTo(2);
        assertThat(findCaseExecutions(caseInstance.getCaseInstanceId(), "task_2").stream().anyMatch(CaseExecution::isEnabled)).isTrue();
        assertThat(findCaseExecutions(caseInstance.getCaseInstanceId(), "task_2").stream().anyMatch(CaseExecution::isAvailable)).isTrue();

        complete(CmmnAwareTests.caseExecution("task_3", caseInstance));

        // Expecting Task_3 to be completed
        assertThat(findHistoricCaseActivityInstance(caseInstance.getCaseInstanceId(), "task_3").get(0).isCompleted()).isTrue();

        // Expecting Task_2 to be available
        assertThat(caseInstance).humanTask("task_2").isAvailable();
    }

    private List<CaseExecution> findCaseExecutions(String caseInstanceId, String activityId) {
        return caseService().createCaseExecutionQuery().activityId(activityId).caseInstanceId(caseInstanceId).list();
    }

    private List<HistoricCaseActivityInstance> findHistoricCaseActivityInstance(String caseInstanceId, String activityId) {
        return historyService().createHistoricCaseActivityInstanceQuery().caseInstanceId(caseInstanceId).caseActivityId(activityId).list();
    }
}
