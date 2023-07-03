package org.jboss.intersmash.testsuite.demo.shrinkwrap;

import org.jboss.intersmash.tools.application.openshift.EAP7ImageOpenShiftApplication;
import org.jboss.intersmash.tools.application.openshift.input.BuildInput;
import org.jboss.intersmash.tools.application.openshift.input.BuildInputBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

public class EAP7ShrinkWrapImageOpenShiftApplication implements EAP7ImageOpenShiftApplication {
	private static String APP_NAME = "hello";
	private final BuildInput buildInput;

	public EAP7ShrinkWrapImageOpenShiftApplication() {
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
