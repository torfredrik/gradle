/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.composite;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.composite.internal.IncludedBuildRegistry;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.initialization.SettingsLoader;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class CompositeBuildSettingsLoader implements SettingsLoader {
    private final SettingsLoader delegate;
    private final NestedBuildFactory nestedBuildFactory;
    private final CompositeContextBuilder compositeContextBuilder;
    private final IncludedBuildRegistry includedBuildRegistry;

    public CompositeBuildSettingsLoader(SettingsLoader delegate, NestedBuildFactory nestedBuildFactory, CompositeContextBuilder compositeContextBuilder, IncludedBuildRegistry includedBuildRegistry) {
        this.delegate = delegate;
        this.nestedBuildFactory = nestedBuildFactory;
        this.compositeContextBuilder = compositeContextBuilder;
        this.includedBuildRegistry = includedBuildRegistry;
    }

    @Override
    public SettingsInternal findAndLoadSettings(GradleInternal gradle) {
        SettingsInternal settings = delegate.findAndLoadSettings(gradle);
        compositeContextBuilder.setRootBuild(settings);

        Map<File, IncludedBuild> includedBuilds = getIncludedBuilds(gradle.getStartParameter(), settings);
        if (!includedBuilds.isEmpty()) {
            gradle.setIncludedBuilds(includedBuilds.values());
            compositeContextBuilder.addIncludedBuilds(includedBuilds.keySet(), nestedBuildFactory);
        }

        return settings;
    }

    private Map<File, IncludedBuild> getIncludedBuilds(StartParameter startParameter, SettingsInternal settings) {
        Map<File, IncludedBuild> includedBuilds = Maps.newLinkedHashMap();
        includedBuilds.putAll(includedBuildRegistry.getIncludedBuilds());

        for (File file : startParameter.getIncludedBuilds()) {
            IncludedBuild includedBuild = includedBuildRegistry.registerBuild(file, nestedBuildFactory);
            includedBuilds.put(file, includedBuild);
        }

        validateBuildNames(includedBuilds.values(), settings);

        return includedBuilds;
    }

    private void validateBuildNames(Collection<IncludedBuild> builds, SettingsInternal settings) {
        Set<String> names = Sets.newHashSet();
        for (IncludedBuild build : builds) {
            String buildName = build.getName();
            if (!names.add(buildName)) {
                throw new GradleException("Included build '" + buildName + "' is not unique in composite.");
            }
            if (settings.getRootProject().getName().equals(buildName)) {
                throw new GradleException("Included build '" + buildName + "' collides with root project name.");
            }
            if (settings.findProject(":" + buildName) != null) {
                throw new GradleException("Included build '" + buildName + "' collides with subproject of the same name.");
            }
        }
    }

}
