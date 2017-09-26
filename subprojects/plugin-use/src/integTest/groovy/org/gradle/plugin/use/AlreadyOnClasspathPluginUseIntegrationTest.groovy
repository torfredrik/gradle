package org.gradle.plugin.use

import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture


class AlreadyOnClasspathPluginUseIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    def "build scripts can request buildSrc plugin by id"() {

        given:
        file("buildSrc/src/main/groovy/my/MyPlugin.groovy") << """
            package my
            
            import org.gradle.api.*
            
            class MyPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    println("buildSrc plugin applied by id!")
                }
            }
        """.stripIndent()

        and:
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/my-plugin.properties") << """
            implementation-class=my.MyPlugin
        """.stripIndent()

        and:
        settingsFile << """

            include("a")

        """.stripIndent()

        and:
        buildFile << """

            plugins {
                id("my-plugin")
            }

        """.stripIndent()

        and:
        file("a/build.gradle") << """

            plugins {
                id("my-plugin")
            }

        """.stripIndent()

        when:
        succeeds "help"

        then:
        outputContains("buildSrc plugin applied by id!")

        and:
        operations.all(ApplyPluginBuildOperationType).each { println(it) }
        operations.hasOperation("Apply plugin 'my-plugin' to root project 'root'")
        operations.hasOperation("Apply plugin 'my-plugin' to project ':a'")
    }

    def "build script can request non-built-in plugin applied to parent project by id"() {

        given:
        settingsFile << """

            include("a")

        """.stripIndent()

        and:
        buildFile << """

            plugins {
                id("com.gradle.plugin-publish") version "0.9.7"
            }

        """.stripIndent()

        and:
        file("a/build.gradle") << """

            plugins {
                id("com.gradle.plugin-publish")
            }

        """.stripIndent()

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to root project 'root'")
        operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to project ':a'")
    }

    def "build script can request non-built-in plugin requested on but not applied to parent project by id"() {

        given:
        settingsFile << """

            include("a")

        """.stripIndent()

        and:
        buildFile << """

            plugins {
                id("com.gradle.plugin-publish") version "0.9.7" apply false
            }

        """.stripIndent()

        and:
        file("a/build.gradle") << """

            plugins {
                id("com.gradle.plugin-publish")
            }

        """.stripIndent()

        when:
        succeeds "help"

        then:
        !operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to root project 'root'")
        operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to project ':a'")
    }

    def "version must not be set on plugin requests resolved as already on classpath"() {

        given:
        settingsFile << """

            include("a")

        """.stripIndent()

        and:
        buildFile << """

            plugins {
                id("com.gradle.plugin-publish") version "0.9.7"
            }

        """.stripIndent()

        and:
        file("a/build.gradle") << """

            plugins {
                id("com.gradle.plugin-publish") version "0.9.7"
            }

        """.stripIndent()

        when:
        fails "help"

        then:
        failure.assertHasCause("Setting version for plugin 'com.gradle.plugin-publish' already on the script classpath is not supported")

        and:
        operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to root project 'root'")
        !operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to project ':a'")
    }

    def "build scripts can request a plugin already on the classpath when a plugin resolution strategy sets a version"() {

        given:
        settingsFile.text = """

            pluginManagement {
                resolutionStrategy {
                    eachPlugin {
                        println(it.requested.class)
                        useVersion("0.9.7")
                    }
                }
            }

            include("a")

        """.stripIndent()

        and:
        buildFile << """

            plugins {
                id("com.gradle.plugin-publish")
            }

        """.stripIndent()

        and:
        file("a/build.gradle") << """

            plugins {
                id("com.gradle.plugin-publish")
            }

        """.stripIndent()

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to root project 'root'")
        operations.hasOperation("Apply plugin 'com.gradle.plugin-publish' to project ':a'")
    }
}
