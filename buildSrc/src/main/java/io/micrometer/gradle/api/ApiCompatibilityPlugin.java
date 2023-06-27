/*
 * Copyright 2023 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.gradle.api;

import japicmp.filter.AnnotationBehaviorFilter;
import japicmp.filter.AnnotationClassFilter;
import japicmp.filter.AnnotationFieldFilter;
import me.champeau.gradle.japicmp.JapicmpPlugin;
import me.champeau.gradle.japicmp.JapicmpTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link Plugin} that applies the {@code "japicmp-gradle-plugin"} and creates a
 * {@value #TASK_NAME} task in the project it's applied to in order to check that the
 * current version is not breaking API compatibility with the {@code "compatibleVersion"}
 * configured as a project property.
 * <p>
 * By default this property is defined in the main {@code "gradle.properties"} file but
 * can be overridden on the command line with
 * {@code "./gradlew check -PcompatibleVersion=1.12.0"}. Setting the compatible version to
 * the special {@code "SKIP"} value or configuring Gradle in offline mode will cancel the
 * configuration of this task.
 *
 * @author Brian Clozel
 */
public class ApiCompatibilityPlugin implements Plugin<Project> {

    public static final String TASK_NAME = "apiCompatibility";

    private static final Logger logger = LoggerFactory.getLogger(ApiCompatibilityPlugin.class);

    private static final String COMPATIBLE_VERSION_PROPERTY = "compatibleVersion";

    private static final URI SPRING_MILESTONE_REPOSITORY = URI.create("https://repo.spring.io/milestone");

    private static final URI SPRING_SNAPSHOT_REPOSITORY = URI.create("https://repo.spring.io/snapshot");

    private static final List<String> EXCLUDED_PACKAGES = Arrays.asList("io.micrometer.shaded.*",
            "io.micrometer.statsd.internal");

    @Override
    public void apply(Project project) {
        if (project.getGradle().getStartParameter().isOffline()) {
            logger.info("Skipping API Compatibility test in offline mode");
        }
        else if (!project.hasProperty(COMPATIBLE_VERSION_PROPERTY)) {
            logger.info("Skipping API Compatibility test since 'compatibleVersion' property is empty");
        }
        else if ("SKIP".equals(project.property(COMPATIBLE_VERSION_PROPERTY))) {
            logger.info("Skipping API Compatibility test since 'compatibleVersion' property is 'SKIP'");
        }
        else {
            project.getPlugins().withType(JavaBasePlugin.class, javaPlugin -> {
                project.getPluginManager().apply(JapicmpPlugin.class);
                project.getPlugins().withType(JapicmpPlugin.class, apiPlugin -> {
                    JapicmpTask apiCompatibilityTask = createApiCompatibilityTask(project);
                    project.getTasks()
                        .getByName(JavaBasePlugin.CHECK_TASK_NAME, check -> check.dependsOn(apiCompatibilityTask));
                    project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME, apiCompatibilityTask::dependsOn);
                });
            });
        }
    }

    private JapicmpTask createApiCompatibilityTask(Project project) {
        String compatibleVersion = project.property(COMPATIBLE_VERSION_PROPERTY).toString();
        logger.info("Perform API Compatibility test with 'compatibleVersion=" + compatibleVersion + "'");
        configureRepository(compatibleVersion, project);

        JapicmpTask apiDiff = project.getTasks().create(TASK_NAME, JapicmpTask.class);
        apiDiff.setDescription("Checks for API backwards compatibility with previous version");
        apiDiff.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        apiDiff.getOldClasspath().setFrom(createBaselineConfiguration(compatibleVersion, project));

        TaskProvider<Jar> jar = project.getTasks().withType(Jar.class).named("jar");

        apiDiff.getNewArchives().setFrom(project.getLayout().files(jar.get().getArchiveFile().get().getAsFile()));
        apiDiff.getNewClasspath().setFrom(getRuntimeClassPath(project));
        apiDiff.getOnlyBinaryIncompatibleModified().set(true);
        apiDiff.getFailOnModification().set(true);
        apiDiff.getFailOnSourceIncompatibility().set(true);
        apiDiff.getIgnoreMissingClasses().set(true);
        apiDiff.getIncludeSynthetic().set(true);
        apiDiff.getCompatibilityChangeExcludes().set(Collections.singletonList("METHOD_NEW_DEFAULT"));
        apiDiff.getPackageExcludes().set(EXCLUDED_PACKAGES);
        // excluded @Deprecated - annotated elements from compatibility checks
        apiDiff.addExcludeFilter(DeprecatedClassFilter.class);
        apiDiff.addExcludeFilter(DeprecatedMethodFilter.class);
        apiDiff.addExcludeFilter(DeprecatedFieldFilter.class);

        apiDiff.getTxtOutputFile().set(getOutputFile(project));

        return apiDiff;
    }

    private void configureRepository(String compatibleVersion, Project project) {
        if (compatibleVersion.contains("-M") || compatibleVersion.contains("-RC")) {
            project.getRootProject()
                .getRepositories()
                .maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(SPRING_MILESTONE_REPOSITORY));
        }
        else if (compatibleVersion.contains("-SNAPSHOT")) {
            project.getRootProject()
                .getRepositories()
                .maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(SPRING_SNAPSHOT_REPOSITORY));
        }
        project.getRootProject().getRepositories().mavenCentral();
    }

    private Configuration createBaselineConfiguration(String baselineVersion, Project project) {
        String baseline = String.join(":", project.getGroup().toString(), project.getName(), baselineVersion);
        Dependency compatibleDependency = project.getDependencies().create(baseline + "@jar");
        Configuration compatibleConfiguration = project.getRootProject()
            .getConfigurations()
            .detachedConfiguration(compatibleDependency);
        try {
            // eagerly resolve the baseline configuration to check whether this is a new
            // module
            compatibleConfiguration.resolve();
            return compatibleConfiguration;
        }
        catch (GradleException exception) {
            logger.warn("Could not resolve {} - assuming this is a new Micrometer module.", baseline);
        }
        return project.getRootProject().getConfigurations().detachedConfiguration();
    }

    private File getOutputFile(Project project) {
        Path outDir = Paths.get(project.getRootProject().getBuildDir().getAbsolutePath(), "reports");
        return project.file(outDir.resolve(project.getName() + ".txt").toString());
    }

    private Configuration getRuntimeClassPath(Project project) {
        return project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
    }

    public static class DeprecatedMethodFilter extends AnnotationBehaviorFilter {

        public DeprecatedMethodFilter() {
            super("@Deprecated");
        }

    }

    public static class DeprecatedClassFilter extends AnnotationClassFilter {

        public DeprecatedClassFilter() {
            super("@Deprecated");
        }

    }

    public static class DeprecatedFieldFilter extends AnnotationFieldFilter {

        public DeprecatedFieldFilter() {
            super("@Deprecated");
        }

    }

}
