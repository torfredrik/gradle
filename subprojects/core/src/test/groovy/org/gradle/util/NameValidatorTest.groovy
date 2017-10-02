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
package org.gradle.util

import org.gradle.api.DefaultTask
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeContainer
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.taskfactory.TaskFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.featurelifecycle.DeprecatedFeatureUsage
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativeplatform.internal.DefaultFlavorContainer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(NameValidator)
class NameValidatorTest extends Specification {
    static forbiddenCharacters = NameValidator.FORBIDDEN_CHARACTERS
    static forbiddenLeadingAndTrailingCharacter = NameValidator.FORBIDDEN_LEADING_AND_TRAILING_CHARACTER
    static invalidNames = forbiddenCharacters.collect { "a${it}b"} + ["${forbiddenLeadingAndTrailingCharacter}ab", "ab${forbiddenLeadingAndTrailingCharacter}"] + ['']

    @Shared
    def domainObjectContainersWithValidation = [
        ["artifact types", new DefaultArtifactTypeContainer(DirectInstantiator.INSTANCE, null)],
        ["configurations", new DefaultConfigurationContainer(null, DirectInstantiator.INSTANCE, Mock(DomainObjectContext), Mock(ListenerManager), null, null, null, null, Mock(FileCollectionFactory), null, null, null, null, null, null)],
        ["flavors",  new DefaultFlavorContainer(DirectInstantiator.INSTANCE)]
    ]

    def loggingDeprecatedFeatureHandler = Mock(LoggingDeprecatedFeatureHandler)

    def setup() {
        SingleMessageLogger.handler = loggingDeprecatedFeatureHandler
    }

    def cleanup() {
        SingleMessageLogger.reset()
    }

    @Unroll
    def "tasks are not allowed to be named '#name'"() {
        when:
        new TaskFactory(Mock(ClassGenerator), null, Mock(Instantiator)).create(name, DefaultTask)

        then:
        1 * loggingDeprecatedFeatureHandler.deprecatedFeatureUsed(_  as DeprecatedFeatureUsage) >> { DeprecatedFeatureUsage usage ->
            assertForbidden(name, usage.message)
        }

        where:
        name << invalidNames
    }

    @Unroll
    def "#objectType are not allowed to be named '#name'"() {
        when:
        domainObjectContainer.create(name)

        then:
        1 * loggingDeprecatedFeatureHandler.deprecatedFeatureUsed(_  as DeprecatedFeatureUsage) >> { DeprecatedFeatureUsage usage ->
            assertForbidden(name, usage.message)
        }

        where:
        [name, objectType, domainObjectContainer] << [invalidNames, domainObjectContainersWithValidation].combinations().collect { [it[0], it[1][0], it[1][1]] }
    }

    def "can handle empty names"() {
        when:
        NameValidator.validate('', '', '')

        then:
        noExceptionThrown()
    }

    void assertForbidden(name, message) {
        if (name == '') {
            assert message.contains("is empty. This has been deprecated and is scheduled to be removed in Gradle 5.0.")
        } else if (name.contains("" + forbiddenLeadingAndTrailingCharacter)) {
            assert message.contains("' starts or ends with a '.'. This has been deprecated and is scheduled to be removed in Gradle 5.0.")
        } else {
            assert message.contains("""' contains at least one of the following characters: [ , /, \\, :, <, >, ", ?, *, |]. This has been deprecated and is scheduled to be removed in Gradle 5.0.""")
        }
    }
}
