package io.fabric8

import org.junit.Before
import org.junit.Test
import utilities.ScriptLoader

import static org.hamcrest.core.IsEqual.equalTo
import static org.junit.Assert.assertThat

class Fabric8CommandsTest {

    def f8Cmds

    @Before
    void setUp() {
        f8Cmds = ScriptLoader.load("src/io/fabric8", "io.fabric8.Fabric8Commands")
    }

    @Test
    void testExtractOrganizationAndProjectFromGitHubUrl() {
        String result = f8Cmds.extractOrganizationAndProjectFromGitHubUrl("https://github.com/digitaldealer/OrganizationPreference.git")
        assertThat(result, equalTo("digitaldealer/OrganizationPreference"))
    }
}
