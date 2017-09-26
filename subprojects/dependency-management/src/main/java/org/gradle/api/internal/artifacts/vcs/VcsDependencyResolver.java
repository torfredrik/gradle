/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.vcs;

import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.composite.internal.IncludedBuildFactory;
import org.gradle.composite.internal.IncludedBuilds;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;
import org.gradle.vcs.internal.VcsMappingFactory;
import org.gradle.vcs.internal.VcsMappingInternal;
import org.gradle.vcs.internal.VcsMappingsInternal;
import org.gradle.vcs.internal.VersionControlSystemFactory;

import java.io.File;
import java.util.Set;

public class VcsDependencyResolver implements DependencyToComponentIdResolver, ComponentResolvers {
    private final ServiceRegistry serviceRegistry;
    private final ProjectDependencyResolver projectDependencyResolver;
    private final LocalComponentRegistry localComponentRegistry;
    private final VcsMappingsInternal vcsMappingsInternal;
    private final VcsMappingFactory vcsMappingFactory;
    private final VersionControlSystemFactory versionControlSystemFactory;
    private final File baseWorkingDir;

    // TODO: This shouldn't reach into ServiceRegistry
    public VcsDependencyResolver(ServiceRegistry serviceRegistry, File rootProjectBuildDir, ProjectDependencyResolver projectDependencyResolver, LocalComponentRegistry localComponentRegistry, VcsMappingsInternal vcsMappingsInternal, VcsMappingFactory vcsMappingFactory, VersionControlSystemFactory versionControlSystemFactory) {
        this.serviceRegistry = serviceRegistry;
        this.projectDependencyResolver = projectDependencyResolver;
        this.localComponentRegistry = localComponentRegistry;
        this.vcsMappingsInternal = vcsMappingsInternal;
        this.vcsMappingFactory = vcsMappingFactory;
        this.versionControlSystemFactory = versionControlSystemFactory;
        this.baseWorkingDir = new File(rootProjectBuildDir, "vcsWorkingDirs");
    }

    @Override
    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        VcsMappingInternal vcsMappingInternal = getVcsMapping(dependency);
        if (vcsMappingInternal != null) {
            vcsMappingsInternal.getVcsMappingRule().execute(vcsMappingInternal);

            // TODO: Need failure handling, e.g., cannot clone repository
            if (vcsMappingInternal.hasRepository()) {
                VersionControlSpec spec = vcsMappingInternal.getRepository();
                VersionControlSystem versionControlSystem = versionControlSystemFactory.create(spec);
                VersionRef selectedVersion = selectVersionFromRepository(spec, versionControlSystem);
                File dependencyWorkingDir = populateWorkingDirectory(spec, versionControlSystem, selectedVersion);

                // TODO: This should only happen once, extract this into some kind of coordinator with explicitly included builds
                IncludedBuild includedBuild = registerIncludedBuild(dependencyWorkingDir);

                // TODO: Populate component registry and implicitly include builds
                String projectPath = ":"; // TODO: This needs to be extracted by configuring the build. Assume it's from the root for now
                LocalComponentMetadata componentMetaData = localComponentRegistry.getComponent(DefaultProjectComponentIdentifier.newProjectId(includedBuild, projectPath));

                if (componentMetaData == null) {
                    // TODO: Error
                    result.failed(new ModuleVersionResolveException(DefaultProjectComponentSelector.newSelector(includedBuild, projectPath), vcsMappingInternal + " not supported yet."));
                } else {
                    result.resolved(componentMetaData);
                }
            }
        } else {
            projectDependencyResolver.resolve(dependency, result);
        }
    }

    private IncludedBuild registerIncludedBuild(File dependencyWorkingDir) {
        IncludedBuilds includedBuilds = serviceRegistry.get(IncludedBuilds.class);
        IncludedBuildFactory includedBuildFactory = serviceRegistry.get(IncludedBuildFactory.class);
        IncludedBuild includedBuild = includedBuildFactory.createBuild(dependencyWorkingDir);
        includedBuilds.registerBuild(includedBuild);
        return includedBuild;
    }

    private File populateWorkingDirectory(VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionRef selectedVersion) {
        // TODO: We need to manage these working directories so they're shared across projects within a build (if possible)
        // and have some sort of global cache of cloned repositories.  This should be separate from the global cache.
        File dependencyWorkingDir = new File(baseWorkingDir, spec.getRepositoryId() + "/" + selectedVersion.getCanonicalId() + "/" + spec.getRepoName());
        versionControlSystem.populate(dependencyWorkingDir, selectedVersion, spec);
        return dependencyWorkingDir;
    }

    private VersionRef selectVersionFromRepository(VersionControlSpec spec, VersionControlSystem versionControlSystem) {
        // TODO: Select version based on requested version and tags
        Set<VersionRef> versions = versionControlSystem.getAvailableVersions(spec);
        return versions.iterator().next();
    }

    private VcsMappingInternal getVcsMapping(DependencyMetadata dependency) {
        // TODO: Only perform source dependency resolution when version == latest.integration for now
        if (vcsMappingsInternal.hasRules() && dependency.getRequested().getVersion().equals("latest.integration")) {
            return vcsMappingFactory.create(dependency.getSelector(), dependency.getRequested());
        }
        return null;
    }

    @Override
    public DependencyToComponentIdResolver getComponentIdResolver() {
        return this;
    }

    @Override
    public ComponentMetaDataResolver getComponentResolver() {
        return projectDependencyResolver;
    }

    @Override
    public OriginArtifactSelector getArtifactSelector() {
        return projectDependencyResolver;
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
        return projectDependencyResolver;
    }
}
