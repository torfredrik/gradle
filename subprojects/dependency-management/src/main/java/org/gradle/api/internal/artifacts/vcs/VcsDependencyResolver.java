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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.composite.internal.IncludedBuildFactory;
import org.gradle.composite.internal.IncludedBuilds;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.internal.VcsMappingFactory;
import org.gradle.vcs.internal.VcsMappingInternal;
import org.gradle.vcs.internal.VcsMappingsInternal;
import org.gradle.vcs.internal.VersionControlSystemFactory;

import java.io.File;

public class VcsDependencyResolver implements DependencyToComponentIdResolver, ComponentResolvers {
    private final ServiceRegistry serviceRegistry;
    private final VcsMappingsInternal vcsMappingsInternal;
    private final VcsMappingFactory vcsMappingFactory;
    private final VersionControlSystemFactory versionControlSystemFactory;
    private final File cacheDir;

    // TODO: This shouldn't reach into ServiceRegistry
    public VcsDependencyResolver(ServiceRegistry serviceRegistry, VcsMappingsInternal vcsMappingsInternal, VcsMappingFactory vcsMappingFactory, VersionControlSystemFactory versionControlSystemFactory) {
        this.serviceRegistry = serviceRegistry;
        this.vcsMappingsInternal = vcsMappingsInternal;
        this.vcsMappingFactory = vcsMappingFactory;
        this.versionControlSystemFactory = versionControlSystemFactory;
        // TODO: We need to have a "cache" for repositories in the VCS implementation and a working dir "cache" for included builds
        // This path shouldn't be hardcoded here
        this.cacheDir = new File("build");
    }

    @Override
    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        VcsMappingInternal vcsMappingInternal = getVcsMapping(dependency);
        if (vcsMappingInternal != null) {
            // TODO: Need failure handling, e.g., cannot clone repository
            vcsMappingsInternal.getVcsMappingRule().execute(vcsMappingInternal);
            if (vcsMappingInternal.isUpdated()) {
                String projectPath = ":"; // TODO: This needs to be extracted by configuring the build. Assume it's from the root for now
                String buildName = vcsMappingInternal.getOldRequested().getName();
                VersionControlSpec spec = vcsMappingInternal.getRepository();
                VersionControlSystem versionControlSystem = versionControlSystemFactory.create(spec);
                // TODO: We need to manage these working directories so they're shared across projects within a build (if possible)
                // and have some sort of global cache of cloned repositories.  This should be separate from the global cache.
                File workingDir = new File(cacheDir, "vcs/" + buildName);
                versionControlSystem.populate(workingDir, spec);
                // TODO: Assuming the default branch for the repository
                // This should be based on something from the repository.
                // e.g., versionControlSystem.listVersions(spec)
                // TODO: Select version based on requested version and tags

                // TODO: This should only happen once, extract this into some kind of coordinator with explicitly included builds
                IncludedBuilds includedBuilds = serviceRegistry.get(IncludedBuilds.class);
                IncludedBuildFactory includedBuildFactory = serviceRegistry.get(IncludedBuildFactory.class);
                LocalComponentRegistry localComponentRegistry = serviceRegistry.get(LocalComponentRegistry.class);
                IncludedBuild includedBuild = includedBuildFactory.createBuild(workingDir);
                includedBuilds.registerBuild(includedBuild);
                // TODO: Populate component registry and implicitly include builds
                LocalComponentMetadata componentMetaData = localComponentRegistry.getComponent(DefaultProjectComponentIdentifier.newProjectId(includedBuild, projectPath));

                if (componentMetaData == null) {
                    // TODO: Error
                    result.failed(new ModuleVersionResolveException(DefaultProjectComponentSelector.newSelector(includedBuild, projectPath), vcsMappingInternal + " not supported yet."));
                } else {
                    result.resolved(componentMetaData);
                }
            }
        }
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
        return new ComponentMetaDataResolver() {
            @Override
            public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {

            }

            @Override
            public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
                return true;
            }
        };
    }

    @Override
    public OriginArtifactSelector getArtifactSelector() {
        return new OriginArtifactSelector() {
            @Override
            public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata configuration, ArtifactTypeRegistry artifactTypeRegistry, ModuleExclusion exclusions) {
                return null;
            }
        };
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
        return new ArtifactResolver() {
            @Override
            public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {

            }

            @Override
            public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {

            }
        };
    }
}
