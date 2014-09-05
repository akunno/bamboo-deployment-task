package com.atlassian.bamboo.plugins.deployment.releaser;

import static com.atlassian.bamboo.plugins.deployment.releaser.DeploymentTask.ENVIRONMENT_CONFIG_KEY;
import static com.atlassian.bamboo.plugins.deployment.releaser.DeploymentTask.RELEASE_VERSION_CONFIG_KEY;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.google.common.collect.Sets;

public class DeploymentTaskConfigurator extends AbstractTaskConfigurator
{
    @NotNull
    @Override
    public Map<String, String> generateTaskConfigMap(@NotNull final ActionParametersMap params, @Nullable final TaskDefinition previousTaskDefinition)
    {
        final Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);
        config.put(RELEASE_VERSION_CONFIG_KEY, params.getString(RELEASE_VERSION_CONFIG_KEY));
        config.put(ENVIRONMENT_CONFIG_KEY, params.getString(ENVIRONMENT_CONFIG_KEY));;

        return config;
    }

    @Override
    public void populateContextForCreate(@NotNull final Map<String, Object> context)
    {
        super.populateContextForCreate(context);

        context.put(RELEASE_VERSION_CONFIG_KEY, "");
        context.put(ENVIRONMENT_CONFIG_KEY, "");
    }

    @Override
    public void populateContextForEdit(@NotNull final Map<String, Object> context, @NotNull final TaskDefinition taskDefinition)
    {
        super.populateContextForEdit(context, taskDefinition);

        context.put(RELEASE_VERSION_CONFIG_KEY, taskDefinition.getConfiguration().get(RELEASE_VERSION_CONFIG_KEY));
        context.put(ENVIRONMENT_CONFIG_KEY, taskDefinition.getConfiguration().get(ENVIRONMENT_CONFIG_KEY));
    }

    @Override
    public void populateContextForView(@NotNull final Map<String, Object> context, @NotNull final TaskDefinition taskDefinition)
    {
        super.populateContextForView(context, taskDefinition);
        context.put(RELEASE_VERSION_CONFIG_KEY, taskDefinition.getConfiguration().get(RELEASE_VERSION_CONFIG_KEY));
        context.put(ENVIRONMENT_CONFIG_KEY, taskDefinition.getConfiguration().get(ENVIRONMENT_CONFIG_KEY));
    }

    @Override
    public void validate(@NotNull final ActionParametersMap params, @NotNull final ErrorCollection errorCollection)
    {
        super.validate(params, errorCollection);

        final String release = params.getString(RELEASE_VERSION_CONFIG_KEY);
        if (StringUtils.isEmpty(release))
        {
            errorCollection.addError(RELEASE_VERSION_CONFIG_KEY, getI18nBean().getText("com.atlassian.bamboo.plugins.deployment.releaser.releaseVersion.empty.error"));
        }

        final String environment = params.getString(ENVIRONMENT_CONFIG_KEY);
        if (StringUtils.isEmpty(environment))
        {
            errorCollection.addError(ENVIRONMENT_CONFIG_KEY, getI18nBean().getText("com.atlassian.bamboo.plugins.deployment.releaser.environment.empty.error"));
        }
    }

    @Override
    public Set<Requirement> calculateRequirements(TaskDefinition taskDefinition, Job job) {
        final Set<Requirement> requirements = Sets.newHashSet();
        taskConfiguratorHelper.addSystemRequirementFromConfiguration(requirements, taskDefinition,
                DeploymentTask.DEPLOYMENT_RELEASER_LABEL, DeploymentTask.DEPLOYMENT_RELEASER_CAPABILITY_PREFIX);
        return requirements;
    }
}
