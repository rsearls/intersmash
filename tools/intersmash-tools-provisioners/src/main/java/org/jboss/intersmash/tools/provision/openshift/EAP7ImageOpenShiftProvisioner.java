/**
 * Copyright (C) 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.intersmash.tools.provision.openshift;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.util.Strings;
import org.jboss.intersmash.tools.IntersmashConfig;
import org.jboss.intersmash.tools.application.openshift.EAP7ImageOpenShiftApplication;
import org.jboss.intersmash.tools.application.openshift.EAP7OpenShiftApplication;
import org.jboss.intersmash.tools.application.openshift.input.BinarySource;
import org.jboss.intersmash.tools.application.openshift.input.BuildInput;
import org.jboss.intersmash.tools.application.openshift.input.GitSource;
import org.slf4j.event.Level;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.builder.builders.PVCBuilder;
import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.builder.builders.pod.VolumeMount;
import cz.xtf.builder.builders.route.TransportProtocol;
import cz.xtf.core.bm.BinaryBuild;
import cz.xtf.core.bm.BinaryBuildFromFile;
import cz.xtf.core.bm.BinarySourceBuild;
import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.bm.ManagedBuildReference;
import cz.xtf.core.event.helpers.EventHelper;
import cz.xtf.core.openshift.OpenShiftWaiters;
import cz.xtf.core.waiting.failfast.FailFastCheck;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Class deploys a EAP7 application based on {@link EAP7OpenShiftApplication}
 */
@Slf4j
public class EAP7ImageOpenShiftProvisioner implements OpenShiftProvisioner<EAP7ImageOpenShiftApplication> {

	private final EAP7ImageOpenShiftApplication eap7Application;
	private final String CLI_LAUNCH_SCRIPT = "CLI_LAUNCH_SCRIPT";
	private FailFastCheck ffCheck = () -> false;

	public EAP7ImageOpenShiftProvisioner(@NonNull EAP7ImageOpenShiftApplication eap7Application) {
		this.eap7Application = eap7Application;
	}

	@Override
	public EAP7ImageOpenShiftApplication getApplication() {
		return eap7Application;
	}

	@Override
	public void deploy() {
		deployImage();
	}

	@Override
	public void undeploy() {
		OpenShiftUtils.deleteResourcesWithLabel(openShift, APP_LABEL_KEY, eap7Application.getName());
		// when using git repo S2I process creates some custom maps and build pods
		openShift.getConfigMaps()
				.stream()
				.filter(cfMap -> cfMap.getMetadata().getName().startsWith(eap7Application.getName()))
				.forEach(openShift::deleteConfigMap);
		openShift.getPods()
				.stream()
				.filter(pod -> pod.getMetadata().getName().startsWith(eap7Application.getName()))
				.forEach(openShift::deletePod);
	}

	@Override
	public void scale(int replicas, boolean wait) {
		openShift.scale(eap7Application.getName(), replicas);
		if (wait) {
			waitForReplicas(replicas);
		}
	}

	public void waitForReplicas(int replicas) {
		OpenShiftWaiters.get(openShift, ffCheck).areExactlyNPodsReady(replicas, eap7Application.getName()).level(Level.DEBUG)
				.waitFor();
		WaitersUtil.serviceEndpointsAreReady(openShift, getApplication().getName(), replicas, 8080)
				.level(Level.DEBUG)
				.waitFor();
		if (replicas > 0) {
			WaitersUtil.routeIsUp(getUrl(eap7Application.getName(), false))
					.level(Level.DEBUG)
					.waitFor();
		}
	}

	private ApplicationBuilder getAppBuilder() {
		BuildInput buildInput = eap7Application.getBuildInput();
		Objects.requireNonNull(buildInput);

		if (BinarySource.class.isAssignableFrom(buildInput.getClass())) {
			BinarySource binarySource = (BinarySource) buildInput;
			log.debug("Create application builder from source (path: {}).", binarySource.getArchive().toString());

			List<EnvVar> environmentVariables = new ArrayList<>(eap7Application.getEnvVars());

			File archiveFile = binarySource.getArchive().toFile();
			if (archiveFile.isDirectory()) {
				/*
				  S2I Binary build which takes as input the source code of a maven project located on the local filesystem;

				  This kind of build corresponds to the following workflows:

				  1. "Maven Project": The maven build is run inside the builder image,
				     E.g.:

						oc new-build --name=wildfly-build-from-source-code \
							--labels=intersmash.app=wildfly-test-app \
							--binary=true \
							--strategy=source \
							--env=ADMIN_USERNAME=admin \
							--env=ADMIN_PASSWORD=pass.1234 \
							--env=MAVEN_ARGS_APPEND="-Dwildfly.ee-feature-pack.location=org.wildfly:wildfly-galleon-pack:27.0.0.Alpha4 -Dwildfly.cloud-feature-pack.location=org.wildfly.cloud:wildfly-cloud-galleon-pack:2.0.0.Alpha4" \
							--image=quay.io/wildfly/wildfly-s2i-jdk11:latest

						oc start-build wildfly-build-from-source-code \
							--from-dir=/some-path/intersmash-tools/intersmash-tools-provisioners/src/test/resources/apps/openshift-jakarta-sample \
							--follow

						oc new-app wildfly-build-from-source-code

				  2. "target/server": The maven build is run on the local machine and then, server and application are uploaded to the builder image,
				  	 E.g.:

				  		cd /path/intersmash/intersmash-tools/intersmash-tools-provisioners/src/test/resources/apps/openshift-jakarta-sample/target/server
						mvn install -P openshift \
							-Dwildfly.ee-feature-pack.location=org.wildfly:wildfly-galleon-pack:27.0.0.Alpha4 \
							-Dwildfly.cloud-feature-pack.location=org.wildfly.cloud:wildfly-cloud-galleon-pack:2.0.0.Alpha4

						oc new-build --name=wildfly-build-from-server \
							--labels=intersmash.app=wildfly-test-app \
							--binary=true \
							--strategy=source \
							--env=ADMIN_USERNAME=admin \
							--env=ADMIN_PASSWORD=pass.1234 \
							--image=quay.io/wildfly/wildfly-s2i-jdk11:latest

						oc start-build wildfly-build-from-server \
							--from-dir=./target/server \
							--follow

						oc new-app wildfly-build-from-server
				 */
				BinaryBuild binaryBuild;
				Path localSourceCode = eap7Application.prepareProjectSources(binarySource.getArchive());
				binaryBuild = new BinarySourceBuild(
						IntersmashConfig.eap7ImageURL(),
						localSourceCode,
						environmentVariables.stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue)),
						eap7Application.getName());
				ManagedBuildReference reference = BuildManagers.get().deploy(binaryBuild);
				BuildManagers.get().hasBuildCompleted(binaryBuild).waitFor();
				return ApplicationBuilder.fromManagedBuild(
						eap7Application.getName(),
						reference,
						Collections.singletonMap(APP_LABEL_KEY, eap7Application.getName()));
			} else if (archiveFile.isFile()) {
				/*
				  Legacy S2I Binary build which takes as input an already built artifact e.g. WAR file;

				  Note that WILDFLY images do not contain the server anymore;

				  This scenario is probably to be pruned: now, if the build of th maven project happens outside
				  openshift, you start a binary build "--from-dir" using the "target/server" folder;

				  This workflow is just preserved to support legacy builds where no server is provisioned because the
				  maven project isn't configured to use the new "wildfly-maven-plugin";
				  E.g.

					oc new-build --name=wildfly-build-from-war \
						--labels=intersmash.app=wildfly-test-app \
						--binary=true \
						--strategy=source \
						--env=ADMIN_USERNAME=admin \
						--env=ADMIN_PASSWORD=pass.1234 \
						--env=GALLEON_PROVISION_FEATURE_PACKS="org.wildfly:wildfly-galleon-pack:27.0.0.Alpha4,org.wildfly.cloud:wildfly-cloud-galleon-pack:2.0.0.Alpha4" \
						--env=GALLEON_PROVISION_LAYERS=cloud-server \
						--image=quay.io/wildfly/wildfly-s2i-jdk11:latest

					oc start-build wildfly-build-from-war \
						--from-file=/some-path/intersmash/intersmash-tools/intersmash-tools-provisioners/src/test/resources/apps/openshift-jakarta-sample/target/ROOT.war \
						--follow

					oc new-app wildfly-build-from-war
				 */
				BinaryBuildFromFile eap7Build = new BinaryBuildFromFile(
						IntersmashConfig.eap7ImageURL(),
						binarySource.getArchive(),
						environmentVariables.stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue)),
						eap7Application.getName() + "-"
								+ IntersmashConfig.getProductCode(IntersmashConfig.eap7ImageURL()));
				ManagedBuildReference reference = BuildManagers.get().deploy(eap7Build);
				BuildManagers.get().hasBuildCompleted(eap7Build).level(Level.DEBUG).waitFor();

				return ApplicationBuilder.fromManagedBuild(eap7Application.getName(), reference,
						Collections.singletonMap(APP_LABEL_KEY, eap7Application.getName()));
			} else {
				throw new RuntimeException(
						String.format("'%s' archive path must be either a directory or a file", archiveFile.getAbsolutePath()));
			}
		} else if (GitSource.class.isAssignableFrom(buildInput.getClass())) {
			/*
			  S2I Build which takes as input, source code located in a remote Git repository;
			 */
			GitSource gitSource = (GitSource) buildInput;
			log.debug("Create application builder from git reference (repo: {}, ref: {}).",
					gitSource.getUri(), gitSource.getRef());
			ApplicationBuilder appBuilder = ApplicationBuilder.fromS2IBuild(eap7Application.getName(),
					IntersmashConfig.eap7ImageURL(),
					gitSource.getUri(),
					Collections.singletonMap(APP_LABEL_KEY, eap7Application.getName()));

			appBuilder.buildConfig().onConfigurationChange().gitRef(gitSource.getRef());
			if (!Strings.isNullOrEmpty(gitSource.getContextDir()))
				appBuilder.buildConfig().onConfigurationChange().gitContextDir(gitSource.getContextDir());

			eap7Application.getEnvVars().stream()
					.forEach(entry -> appBuilder.buildConfig().sti().addEnvVariable(entry.getName(), entry.getValue()));
			return appBuilder;
		} else {
			throw new RuntimeException("Application artifact path, git reference or maven project root has to be specified");
		}
	}

	private void deployImage() {
		ffCheck = FailFastUtils.getFailFastCheck(EventHelper.timeOfLastEventBMOrTestNamespaceOrEpoch(),
				eap7Application.getName());
		ApplicationBuilder appBuilder = getAppBuilder();
		appBuilder.service()
				.port("8080-tcp", 8080, 8080, TransportProtocol.TCP);

		appBuilder.deploymentConfig().podTemplate().container()
				.addLivenessProbe()
				.setInitialDelay(60)
				.setFailureThreshold(6)
				.createHttpProbe("/health/live", "9990");

		appBuilder.deploymentConfig().podTemplate().container()
				.addReadinessProbe()
				.setFailureThreshold(6)
				.createHttpProbe("/health/ready", "9990");

		// setup the ping service for clustering using DNS_PING
		if (eap7Application.getPingServiceName() != null) {
			String pingServiceName = eap7Application.getPingServiceName();
			int pingServicePort = 8888;
			appBuilder.service(eap7Application.getPingServiceName())
					.addAnnotation("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true")
					.headless()
					.port("ping", pingServicePort, pingServicePort, TransportProtocol.TCP);
			Map<String, String> pingServiceEnv = new HashMap<>();
			pingServiceEnv.put("JGROUPS_PING_PROTOCOL", "dns.DNS_PING");
			pingServiceEnv.put("OPENSHIFT_DNS_PING_SERVICE_NAME", pingServiceName);
			pingServiceEnv.put("OPENSHIFT_DNS_PING_SERVICE_PORT", String.valueOf(pingServicePort));
			appBuilder.deploymentConfig().podTemplate().container().envVars(Collections.unmodifiableMap(pingServiceEnv));
		}

		// mount postconfigure CLI commands
		if (!eap7Application.getCliScript().isEmpty()) {
			final String extensionPath = "/opt/server/extensions";
			final String scriptName = "configure.cli";

			appBuilder.configMap("jboss-cli")
					.configEntry(scriptName, String.join("\n", eap7Application.getCliScript()));

			appBuilder.deploymentConfig()
					.podTemplate()
					.addConfigMapVolume("jboss-cli", "jboss-cli", "0755")
					.container()
					.addVolumeMount("jboss-cli", extensionPath, false);

			if (eap7Application.getEnvVars().stream().noneMatch((envVar -> envVar.getName().equals(CLI_LAUNCH_SCRIPT)))) {
				// Application doesn't provide necessary env variable value to the extension script, so let's define it here.
				addEnvVariable(appBuilder, CLI_LAUNCH_SCRIPT, extensionPath + "/" + scriptName, true,
						!BinarySource.class.isAssignableFrom(eap7Application.getBuildInput().getClass()));
			}
		}

		// mount secrets to /etc/secrets
		for (Secret secret : eap7Application.getSecrets()) {
			appBuilder.deploymentConfig().podTemplate()
					.addSecretVolume(secret.getMetadata().getName(), secret.getMetadata().getName())
					.container()
					.addVolumeMount(secret.getMetadata().getName(), "/etc/secrets", false);
		}

		appBuilder.route().targetPort(8080);

		// env vars
		appBuilder.deploymentConfig().podTemplate().container()
				.envVars(eap7Application.getEnvVars().stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue)));

		// enable script debugging
		if (eap7Application.getEnvVars().stream().noneMatch((envVar -> envVar.getName().equals(SCRIPT_DEBUG)))) {
			if (IntersmashConfig.scriptDebug() != null)
				addEnvVariable(appBuilder, SCRIPT_DEBUG, IntersmashConfig.scriptDebug(), true,
						!BinarySource.class.isAssignableFrom(eap7Application.getBuildInput().getClass()));
		}

		// mount persistent volumes into pod
		if (!eap7Application.getPersistentVolumeClaimMounts().isEmpty()) {
			eap7Application.getPersistentVolumeClaimMounts().entrySet().stream()
					.forEach(entry -> {
						PersistentVolumeClaim pvc = entry.getKey();
						Set<VolumeMount> vms = entry.getValue();
						appBuilder.deploymentConfig().podTemplate().addPersistenVolumeClaim(pvc.getName(),
								pvc.getClaimName());
						vms.forEach(vm -> appBuilder.deploymentConfig().podTemplate().container().addVolumeMount(pvc.getName(),
								vm.getMountPath(), vm.isReadOnly(), vm.getSubPath()));
						openShift.createPersistentVolumeClaim(
								new PVCBuilder(pvc.getClaimName()).accessRWX().storageSize("100Mi").build());
					});
		}

		appBuilder.buildApplication(openShift).deploy();
		OpenShiftWaiters.get(openShift, ffCheck).isDcReady(eap7Application.getName()).level(Level.DEBUG).waitFor();
		// 1 by default
		waitForReplicas(1);
	}

	private void addEnvVariable(ApplicationBuilder appBuilder, final String key, final String value, final boolean addToDC,
			final boolean addToBuild) {
		if (addToDC) {
			appBuilder.deploymentConfig().podTemplate().container().envVar(key, value);
		}
		if (addToBuild) {
			appBuilder.buildConfig().sti().addEnvVariable(key, value);
		}
	}

	@Override
	public List<Pod> getPods() {
		return openShift.getPods(getApplication().getName());
	}
}
