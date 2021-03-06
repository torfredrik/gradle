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

package org.gradle.internal.buildoption;

import org.gradle.cli.CommandLineOption;
import org.gradle.cli.CommandLineParser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides a basic infrastructure for build option implementations.
 *
 * @since 4.3
 */
public abstract class AbstractBuildOption<T> implements BuildOption<T> {

    protected final String gradleProperty;
    protected final List<CommandLineOptionConfiguration> commandLineOptionConfigurations;

    public AbstractBuildOption(String gradleProperty) {
        this(gradleProperty, new CommandLineOptionConfiguration[] {});
    }

    public AbstractBuildOption(String gradleProperty, CommandLineOptionConfiguration... commandLineOptionConfiguration) {
        this.gradleProperty = gradleProperty;
        this.commandLineOptionConfigurations = commandLineOptionConfiguration != null ? Arrays.asList(commandLineOptionConfiguration) : Collections.<CommandLineOptionConfiguration>emptyList();

    }

    @Override
    public String getGradleProperty() {
        return gradleProperty;
    }

    protected boolean isTrue(String value) {
        return value != null && value.trim().equalsIgnoreCase("true");
    }

    protected CommandLineOption configureCommandLineOption(CommandLineParser parser, String[] options, String description, String deprecationWarning, boolean incubating) {
        CommandLineOption option = parser.option(options)
            .hasDescription(description)
            .deprecated(deprecationWarning);

        if (incubating) {
            option.incubating();
        }

        return option;
    }
}
