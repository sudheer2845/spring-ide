/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops;

import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudApplicationDeploymentProperties;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudDashElement;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudFoundryBootDashModel;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.DevtoolsUtil;
import org.springframework.ide.eclipse.boot.dash.model.RunState;

/**
 * Operation for (re)starting existing CF app with associated project
 *
 * @author Alex Boyko
 *
 */
public class FullApplicationRestartOperation extends CloudApplicationOperation {

	private final RunState runOrDebug;

	public FullApplicationRestartOperation(String opName, CloudFoundryBootDashModel model, String appName, RunState runOrDebug) {
		super(opName, model, appName);
		this.runOrDebug = runOrDebug;
	}

	@Override
	protected void doCloudOp(IProgressMonitor monitor) throws Exception, OperationCanceledException {
		CloudApplication application = requests.getApplication(appName);
		if (application == null) {
			throw new CoreException(new Status(IStatus.ERROR, BootDashActivator.PLUGIN_ID, "No Cloud Application found for '" + appName + "'"));
		}
		CloudDashElement cde = model.getElement(appName);
		if (cde == null || cde.getProject() == null) {
			throw new CoreException(new Status(IStatus.ERROR, BootDashActivator.PLUGIN_ID, "Local project not associated to CF app '" + appName + "'"));
		}
		IProject project = cde.getProject();
		CloudApplicationDeploymentProperties properties = CloudApplicationDeploymentProperties.getFor(application, project);
		DevtoolsUtil.setupEnvVarsForRemoteClient(properties.getEnvironmentVariables(), DevtoolsUtil.getSecret(project), runOrDebug);

		List<CloudApplicationOperation> ops = new ArrayList<CloudApplicationOperation>();
		ops.add(new ApplicationUploadOperation(properties, model));
		ops.add(new ApplicationPropertiesUpdateOperation(properties, model));
		ops.add(new ApplicationStartOperation(properties.getAppName(), model));

		CloudApplicationOperation op = new ApplicationOperationWithModelUpdate(getName(), model, appName,
				ops, true);
		op.addApplicationUpdateListener(new FullAppDeploymentListener(appName, model));
		op.run(monitor);
	}

}