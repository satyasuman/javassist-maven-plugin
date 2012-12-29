/*
 * Copyright 2012 http://github.com/drochetti/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.drochetti.javassist.maven;

import static java.lang.Thread.currentThread;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import javassist.ClassPool;
import javassist.LoaderClassPath;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Maven plugin that will apply <a href="http://www.csg.ci.i.u-tokyo.ac.jp/~chiba/javassist/">Javassist</a>
 * class transformations on compiled classes (bytecode instrumentation).
 * @author Daniel Rochetti
 */
@Mojo(name = "javassist", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
	requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class JavassistMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", property = "project", required = true, readonly = true)
	private MavenProject project;

	@Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
	private File outputDirectory;

	@Parameter(defaultValue = "true", property = "includeTestClasses", required = true)
	private Boolean includeTestClasses;

	@Parameter(property = "transformerClasses", required = true)
	private String[] transformerClasses;

	public void execute() throws MojoExecutionException {
		try {
			final List<URL> classPath = new ArrayList<URL>();
			final String outputDirectory = project.getBuild().getOutputDirectory();
			final String testOutputDirectory = project.getBuild().getTestOutputDirectory();
			final List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
			for (String runtimeResource : runtimeClasspathElements) {
				classPath.add(resolveUrl(runtimeResource));
			}
			classPath.add(resolveUrl(outputDirectory));
			if (includeTestClasses) {
				classPath.add(resolveUrl(testOutputDirectory));
			}
			loadClassPath(classPath.toArray(new URL[classPath.size()]));

			ClassLoader contextClassLoader = currentThread().getContextClassLoader();
			Class<ClassTransformer> transformerType = ClassTransformer.class;
			for (String transformerClassName : transformerClasses) {
				Class transformerClass = Class.forName(transformerClassName.trim(), true, contextClassLoader);
				if (transformerType.isAssignableFrom(transformerClass)) {
					ClassTransformer transformer = transformerType.cast(transformerClass.newInstance());
					transformer.transform(outputDirectory);
					if (includeTestClasses) {
						transformer.transform(testOutputDirectory);
					}
				} else {
					throw new MojoExecutionException("Transformer class must inherit from " + transformerType.getName());
				}
			}

		} catch (Exception e) {
			e.printStackTrace(System.err);
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	private void loadClassPath(URL... urls) {
		ClassLoader contextClassLoader = currentThread().getContextClassLoader();
		URLClassLoader pluginClassLoader = URLClassLoader.newInstance(urls, contextClassLoader);
		ClassPool.getDefault().insertClassPath(new LoaderClassPath(pluginClassLoader));
		currentThread().setContextClassLoader(pluginClassLoader);
	}

	private URL resolveUrl(String resource) {
		try {
			return new File(resource).toURI().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

}