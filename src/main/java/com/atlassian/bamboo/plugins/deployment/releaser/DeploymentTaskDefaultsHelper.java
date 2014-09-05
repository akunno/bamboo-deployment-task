package com.atlassian.bamboo.plugins.deployment.releaser;

import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.AbstractFileCapabilityDefaultsHelper;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class DeploymentTaskDefaultsHelper extends AbstractFileCapabilityDefaultsHelper {
    
    private static final Logger log = Logger.getLogger(DeploymentTaskDefaultsHelper.class);
    
    @NotNull
    @Override
    protected String getExecutableName() {
        return DeploymentTask.DEPLOYMENT_RELEASER_EXE_NAME;
    }
    @NotNull
    @Override
    protected String getCapabilityKey() {
        return DeploymentTask.DEPLOYMENT_RELEASER_CAPABILITY_PREFIX + "." + DeploymentTask.DEPLOYMENT_RELEASER_LABEL;
    }
    @NotNull
    public CapabilitySet addDefaultCapabilities(@NotNull final CapabilitySet capabilitySet) {
        Capability capability = new CapabilityImpl(DeploymentTask.DEPLOYMENT_RELEASER_CAPABILITY_PREFIX + "." + DeploymentTask.DEPLOYMENT_RELEASER_LABEL, DeploymentTask.DEPLOYMENT_RELEASER_EXE_NAME);
        capabilitySet.addCapability(capability);
        return capabilitySet;
    }
}
