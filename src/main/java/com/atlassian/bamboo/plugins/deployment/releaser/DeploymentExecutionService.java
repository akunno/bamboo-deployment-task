package com.atlassian.bamboo.plugins.deployment.releaser;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.atlassian.bamboo.deployments.environments.Environment;
import com.atlassian.bamboo.plugins.deployment.releaser.AtlassianHttpService.HttpMethod;
import com.atlassian.bamboo.spring.ComponentAccessor;
import com.atlassian.bamboo.task.TaskContext;
import com.atlassian.plugin.webresource.UrlMode;

public class DeploymentExecutionService {
    
    private TaskContext taskContext;
    public DeploymentExecutionService(TaskContext taskContext) {
        this.taskContext = taskContext;
    }

	public void executeDeployment(Environment environment, String dependencyParentKey, String releaseVersion) throws Exception {
		
	    String username = taskContext.getBuildContext().getVariableContext().getDefinitions().get("bambooApiUserName").getValue();
        String password = taskContext.getBuildContext().getVariableContext().getDefinitions().get("bambooApiPassword").getValue();
	    
		final String baseUrl = ComponentAccessor.WEB_RESOURCE_URL_PROVIDER.get().getBaseUrl(UrlMode.ABSOLUTE);

        AtlassianHttpService httpService = new AtlassianHttpService();
        System.err.println(baseUrl);
        	
	        URL url = new URL(baseUrl + "/userlogin.action");
	        Map<String,String> loginParameters = new HashMap<String,String>();
	        
	        // this isn't going to stay this way - the password is woeful. Should be brought in via a param.
	        loginParameters.put("checkBoxFields", "os_cookie"); 
	        loginParameters.put("os_destination", "/allPlans.action"); 
	        loginParameters.put("os_username", username);
	        loginParameters.put("os_password", password);
	        loginParameters.put("save", "Log in");
			httpService.makeRequest(url, HttpMethod.POST, loginParameters);
	        
	        url = new URL(baseUrl + "/deploy/executeManualDeployment.action");
	        	    		        
	        Map<String,String> deploymentParameters = new HashMap<String,String>();
	        deploymentParameters.put("environmentId", ""+environment.getId());
	        deploymentParameters.put("promoteVersion", "__LOADING__");
	        deploymentParameters.put("releaseTypeOption", "CREATE");
	        deploymentParameters.put("newReleaseBuildResult", dependencyParentKey);
	        deploymentParameters.put("save", "Start deployment");
	        deploymentParameters.put("versionName", releaseVersion);
	        
	        httpService.makeRequest(url, HttpMethod.POST, deploymentParameters);
	}
}
