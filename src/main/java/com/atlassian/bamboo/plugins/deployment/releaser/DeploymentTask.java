package com.atlassian.bamboo.plugins.deployment.releaser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.build.PlanDependencyManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.deployments.DeploymentException;
import com.atlassian.bamboo.deployments.environments.Environment;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.deployments.results.DeploymentResult;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.deployments.versions.DeploymentVersion;
import com.atlassian.bamboo.deployments.versions.service.DeploymentVersionService;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.spring.ComponentAccessor;
import com.atlassian.bamboo.task.BuildTaskRequirementSupport;
import com.atlassian.bamboo.task.TaskContext;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.task.TaskType;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityDefaultsHelper;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.trigger.TriggerReason;
import com.google.common.collect.Sets;

public class DeploymentTask implements TaskType {
    public static final String RELEASE_VERSION_CONFIG_KEY = "releaseVersion";
    public static final String ENVIRONMENT_CONFIG_KEY = "environment";
    
    public static final String DEPLOYMENT_RELEASER_CAPABILITY_PREFIX = CapabilityDefaultsHelper.CAPABILITY_BUILDER_PREFIX + ".bambooServer";
    public static final String DEPLOYMENT_RELEASER_EXE_NAME = "bamboo.exe";
    public static final String DEPLOYMENT_RELEASER_LABEL = "Bamboo Server";


    private BuildLogger buildLogger;
    
    private DeploymentVersionService deploymentVersionService;
    
    public void setDeploymentVersionService(DeploymentVersionService deploymentVersionService) {
        this.deploymentVersionService = deploymentVersionService;
    }

    protected List<Environment> getTargetDeploymentEnvironmentsForPlan(Plan plan) {
        DeploymentProjectService deploymentProjectService = ComponentAccessor.DEPLOYMENT_PROJECT_SERVICE.get();
        List<Environment> deploymentTargets = new ArrayList<Environment>();
        //for (String artifactsPlan : parentPlans) {
            List<DeploymentProject> deploymentProjects;
            boolean isBranchPlan = (plan.getMaster() != null);
            if (isBranchPlan) {
                deploymentProjects = deploymentProjectService.getDeploymentProjectsRelatedToPlan(plan.getMaster().getPlanKey());
            } else {
                deploymentProjects = deploymentProjectService.getDeploymentProjectsRelatedToPlan(plan.getPlanKey());
            }
            buildLogger.addBuildLogEntry("Deployment Projects relating to this plan: " + deploymentProjects.size());
            for (DeploymentProject deploymentProject : deploymentProjects) {
                buildLogger.addBuildLogEntry("Deployment Project: " + deploymentProject.getName());
                deploymentTargets.addAll(deploymentProject.getEnvironments());
            }
        //}
        return deploymentTargets;
    }

    @NotNull
    @java.lang.Override
    public TaskResult execute(@NotNull final TaskContext taskContext) throws TaskException {

        buildLogger = taskContext.getBuildLogger();

        final String buildResultKey = taskContext.getBuildContext().getBuildResultKey();
        final String releaseVersion = taskContext.getConfigurationMap().get(RELEASE_VERSION_CONFIG_KEY);
        final String environmentKey = taskContext.getConfigurationMap().get(ENVIRONMENT_CONFIG_KEY);
        // final String dependencyParentKeyString = taskContext.getConfigurationMap().get(DEPENDENCY_PARENT_CONFIG_KEY);
        
        final PlanResultKey dependencyParentKey = taskContext.getBuildContext().getParentBuildContext().getPlanResultKey();
        
        final Plan plan = ComponentAccessor.PLAN_MANAGER.get().getPlanById(taskContext.getBuildContext().getParentBuildContext().getPlanId());
        final TaskResultBuilder builder = TaskResultBuilder.create(taskContext).failed(); // Initially set to failed

        buildLogger.addBuildLogEntry("Deployment Started: " + buildResultKey + " - looking for environemnts relating to plan " + plan.getPlanKey());
        
        List<Environment> targetDeploymentEnvironments = getTargetDeploymentEnvironmentsForPlan(plan);
        // dependencyParentKey = PlanKeys.getPlanResultKey(dependencyParentKeyString);
        
        try {
            // Need to check if a deployment is already in progress....
            for (Environment environment : targetDeploymentEnvironments) {
                if (environment.getName().equals(environmentKey)) {
                    buildLogger.addBuildLogEntry("Trigger deployment " + dependencyParentKey);
                    waitForDeploymentsToComplete(environment);
                    
                    DeploymentProject parentProject = ComponentAccessor.DEPLOYMENT_PROJECT_SERVICE.get().getDeploymentProjectForEnvironment(environment.getId());
                    DeploymentVersion deploymentVersion = deploymentVersionService.createDeploymentVersion(parentProject.getId(), dependencyParentKey);
                    com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService bambooDeploymentExecutionService = ComponentAccessor.DEPLOYMENT_EXECUTION_SERVICE.get();
                    //bambooDeploymentExecutionService.isEnvironmentBeingDeployedTo(environmentId)
                    TriggerReason buildTriggerReason = taskContext.getBuildContext().getTriggerReason();
                    deploymentVersionService.renameVersion(parentProject.getId(), deploymentVersion, releaseVersion);
                    DeploymentContext deploymentContext = bambooDeploymentExecutionService.prepareDeploymentContext(environment, deploymentVersion, buildTriggerReason);
                    bambooDeploymentExecutionService.execute(deploymentContext);

                    waitForDeploymentsToComplete(environment);  
                    
                    BuildState buildState;
                    do {
                        buildLogger.addBuildLogEntry("Build State unknown, waiting");
                        Thread.sleep(5000);
                        //bambooDeploymentExecutionService.processDeploymentResult(deploymentContext);
                        //CurrentResult currentResult = deploymentContext.getCurrentResult();
                        DeploymentResult result = ComponentAccessor.DEPLOYMENT_RESULT_SERVICE.get().getDeploymentResult(deploymentContext.getDeploymentResultId());
                        buildState = result.getDeploymentState();
                        buildLogger.addBuildLogEntry("Deployment completed - state = " + buildState);
                        //buildLogger.addBuildLogEntry("Current Result - state = " + currentResult.getBuildState());
                    } while(buildState == BuildState.UNKNOWN);
                    
                    if (buildState == BuildState.FAILED) {
                        throw new DeploymentException("Deployment marked as failed. Failing build as well.");
                    }
                    
                }
           }
           builder.success();
        } catch(Exception e) {
            buildLogger.addErrorLogEntry(e.getMessage(), e);
        }
        
        return builder.build();
    }

    private void waitForDeploymentsToComplete(Environment environment) {
        com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService atlassianExecutionService = ComponentAccessor.DEPLOYMENT_EXECUTION_SERVICE.get();
        while(atlassianExecutionService.isEnvironmentBeingDeployedTo(environment.getId())) {
            try {
                buildLogger.addBuildLogEntry("Deployment already in progress - delaying 5 seconds");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                buildLogger.addErrorLogEntry(e.getMessage(), e);
            }
        }
    }
}
