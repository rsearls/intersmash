package org.jboss.intersmash.demos.wildfly.legacy.shrinkwrap;

import org.jboss.intersmash.tools.application.openshift.LegacyWildflyImageOpenShiftApplication;
import org.jboss.intersmash.tools.application.openshift.input.BuildInput;
import org.jboss.intersmash.tools.application.openshift.input.BuildInputBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

public class LegacyWildflyShrinkWrapImageOpenShiftApplication implements LegacyWildflyImageOpenShiftApplication {
	private static String APP_NAME = "hello";
	private final BuildInput buildInput;

	public LegacyWildflyShrinkWrapImageOpenShiftApplication() {
		final WebArchive webArchive = ShrinkWrap.create(WebArchive.class, APP_NAME + ".war")
				.addClasses(HelloService.class, HelloWorldServlet.class)
				.addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
		buildInput = new BuildInputBuilder().archive(webArchive).build();
	}

	@Override
	public BuildInput getBuildInput() {
		return buildInput;
	}

	@Override
	public String getName() {
		return APP_NAME;
	}
}
