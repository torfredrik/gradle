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

package org.gradle.composite.internal;

import com.google.common.collect.Maps;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.specs.Spec;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class DefaultIncludedBuildRegistry implements IncludedBuildRegistry {
    private final IncludedBuildFactory includedBuildFactory;
    // TODO: Locking around this
    private final Map<File, ConfigurableIncludedBuild> includedBuilds = Maps.newLinkedHashMap();


    public DefaultIncludedBuildRegistry(IncludedBuildFactory includedBuildFactory) {
        this.includedBuildFactory = includedBuildFactory;
    }

    @Override
    public boolean hasIncludedBuilds() {
        return !includedBuilds.isEmpty();
    }

    @Override
    public Map<File, IncludedBuild> getIncludedBuilds() {
        return Collections.<File, IncludedBuild>unmodifiableMap(includedBuilds);
    }

    @Override
    public ConfigurableIncludedBuild registerBuild(File buildDirectory, NestedBuildFactory nestedBuildFactory) {
        // TODO: synchronization
        ConfigurableIncludedBuild includedBuild = includedBuilds.get(buildDirectory);
        if (includedBuild == null) {
            includedBuild = includedBuildFactory.createBuild(buildDirectory, nestedBuildFactory);
            includedBuilds.put(buildDirectory, includedBuild);
        }
        return includedBuild;
    }

    @Override
    public IncludedBuild getBuild(final String name) {
        return CollectionUtils.findFirst(includedBuilds.values(), new Spec<IncludedBuild>() {
            @Override
            public boolean isSatisfiedBy(IncludedBuild includedBuild) {
                return includedBuild.getName().equals(name);
            }
        });
    }
}
