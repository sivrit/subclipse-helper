package fr.sivrit.svn.helper.java.tools;

import static fr.sivrit.svn.helper.java.tools.ProjectUtils.fillFromManifest;
import static fr.sivrit.svn.helper.java.tools.ProjectUtils.findPluginName;
import static fr.sivrit.svn.helper.java.tools.ProjectUtils.findProjectName;
import static fr.sivrit.svn.helper.java.tools.ProjectUtils.findProjectNatures;
import static fr.sivrit.svn.helper.java.tools.ProjectUtils.splitLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import fr.sivrit.svn.helper.java.repo.ProjectDeps;

public class ProjectUtilsTest {
    private final static String sampleProjectFile = "<projectDescription>\n"
            + "  <name>ProjectName</name>\n" + "  <comment/>\n"
            + "  <projects><project>referenced.project</project></projects>\n" + "  <buildSpec>\n"
            + "    <buildCommand>\n" + "      <name>org.eclipse.pde.ManifestBuilder</name>\n"
            + "      <arguments></arguments>\n" + "    </buildCommand>\n" + "    <buildCommand>\n"
            + "      <name>org.eclipse.pde.SchemaBuilder</name>\n" + "      <arguments>\n"
            + "      </arguments>\n" + "    </buildCommand>\n" + "    <buildCommand>\n"
            + "      <name>org.scala-ide.sdt.core.scalabuilder</name>\n" + "      <arguments>\n"
            + "      </arguments>\n" + "    </buildCommand>\n" + "  </buildSpec>\n"
            + "  <natures>\n" + "    <nature>org.scala-ide.sdt.core.scalanature</nature>\n"
            + "    <nature>org.eclipse.jdt.core.javanature</nature>\n"
            + "    <nature>org.eclipse.pde.PluginNature</nature>\n" + "  </natures>\n"
            + "</projectDescription>";

    private final static String projectFileWithNoNature = "    <projectDescription>"//
            + "      <name>fr.some.thing</name>" //
            + "      <comment></comment>" //
            + "      <projects> </projects>" //
            + "      <buildSpec>" //
            + "      </buildSpec>" //
            + "      <natures>" //
            + "      </natures>" //
            + "    </projectDescription>";

    private final static String sampleCompleteManifest = "Manifest-Version: 1.0\n"
            + "Bundle-ManifestVersion: 2\n" + "Bundle-Name: BundleName\n"
            + "Bundle-SymbolicName: bundle.symbolic.name;singleton:=true\n"
            + "Bundle-Version: 1.0.0.qualifier\n"
            + "Bundle-Activator: fr.sivrit.svn.helper.java.ActivatorJava\n"
            + "Require-Bundle: org.eclipse.core.runtime,\n"
            + " org.eclipse.core.resources;bundle-version=\"1.0.0\",\n"
            + " org.eclipse.core.stuff,\n"
            + " org.eclipse.core.more.stuff;bundle-version=\"1.0.0\"\n"
            + "Bundle-ActivationPolicy: lazy\n"
            + "Bundle-RequiredExecutionEnvironment: JavaSE-1.6\n";

    private final static String sampleSimpleManifest = "Manifest-Version: 1.0\n"
            + "Bundle-Name: BundleName\n" + "Require-Bundle: org.eclipse.core.runtime\n"
            + "Bundle-SymbolicName: bundle.symbolic.name\n";

    private final static String sampleTrickyManifestA = "Manifest-Version: 1.0\n"
            + "Bundle-Name: BundleName\n"
            + "Bundle-SymbolicName: bundle.symbolic.name;singleton:=false\n"
            + "Require-Bundle: org.eclipse.core.runtime;bundle-version=\"1.0.0\"\n";

    private final static String sampleTrickyManifestB = "Manifest-Version: 1.0\n"
            + "Bundle-Name: BundleName\n"
            + "Require-Bundle: org.eclipse.core.runtime;bundle-version=\"1.0.0\",\n"
            + " org.eclipse.core.resources\n"
            + "Bundle-SymbolicName: bundle.symbolic.name;singleton:=false\n";

    private final static String sampleNoDepManifest = "Manifest-Version: 1.0\n"
            + "Bundle-Name: BundleName\n" + "Bundle-SymbolicName: bundle.symbolic.name\n";

    @Test
    public void verifyFindProjectName() throws IOException {
        assertEquals("ProjectName", findProjectName(sampleProjectFile));
    }

    @Test
    public void verifyEmptyProjectNature() throws IOException {
        assertTrue(findProjectNatures(projectFileWithNoNature).isEmpty());
    }

    @Test
    public void verifyProjectNatures() throws IOException {
        final Set<String> natures = findProjectNatures(sampleProjectFile);

        assertEquals(3, natures.size());
        assertTrue(natures.contains("org.scala-ide.sdt.core.scalanature"));
        assertTrue(natures.contains("org.eclipse.jdt.core.javanature"));
        assertTrue(natures.contains("org.eclipse.pde.PluginNature"));
    }

    @Test
    public void verifyFindPluginName() {
        assertEquals("bundle.symbolic.name", findPluginName(sampleCompleteManifest));
        assertEquals("bundle.symbolic.name", findPluginName(sampleSimpleManifest));
        assertEquals("bundle.symbolic.name", findPluginName(sampleTrickyManifestA));
        assertEquals("bundle.symbolic.name", findPluginName(sampleTrickyManifestB));
        assertEquals("bundle.symbolic.name", findPluginName(sampleNoDepManifest));
    }

    @Test
    public void verifyCompleteFindPluginDepsComplete() {
        final ProjectDeps deps = new ProjectDeps();
        fillFromManifest(deps, splitLines(sampleCompleteManifest));

        final Set<String> expected = new HashSet<String>();
        Collections.addAll(expected, "org.eclipse.core.runtime", "org.eclipse.core.resources",
                "org.eclipse.core.stuff", "org.eclipse.core.more.stuff");

        assertEquals(expected, deps.getPluginDeps());
    }

    @Test
    public void verifySimpleFindPluginDepsComplete() {
        final ProjectDeps deps = new ProjectDeps();
        fillFromManifest(deps, splitLines(sampleSimpleManifest));

        final Set<String> expected = new HashSet<String>();
        Collections.addAll(expected, "org.eclipse.core.runtime");

        assertEquals(expected, deps.getPluginDeps());
    }

    @Test
    public void verifyTrikyAFindPluginDepsComplete() {
        final ProjectDeps deps = new ProjectDeps();
        fillFromManifest(deps, splitLines(sampleTrickyManifestA));

        final Set<String> expected = new HashSet<String>();
        Collections.addAll(expected, "org.eclipse.core.runtime");

        assertEquals(expected, deps.getPluginDeps());
    }

    @Test
    public void verifyTrikyBFindPluginDepsComplete() {
        final ProjectDeps deps = new ProjectDeps();
        fillFromManifest(deps, splitLines(sampleTrickyManifestB));

        final Set<String> expected = new HashSet<String>();
        Collections.addAll(expected, "org.eclipse.core.runtime", "org.eclipse.core.resources");

        assertEquals(expected, deps.getPluginDeps());
    }

    @Test
    public void verifyNoDepFindPluginDepsComplete() {
        final ProjectDeps deps = new ProjectDeps();
        fillFromManifest(deps, splitLines(sampleNoDepManifest));

        assertEquals(Collections.emptySet(), deps.getPluginDeps());
    }

    @Test
    public void verifyDoMatch() {
        final ProjectDeps a = new ProjectDeps("projectA", "pluginA");
        final ProjectDeps b = new ProjectDeps("projectB", "pluginB");
        final ProjectDeps aProject = new ProjectDeps("projectA", null);
        final ProjectDeps aPlugin = new ProjectDeps(null, "pluginA");

        assertTrue(ProjectDeps.doMatch(a, a));

        assertFalse(ProjectDeps.doMatch(a, b));
        assertFalse(ProjectDeps.doMatch(b, a));

        assertTrue(ProjectDeps.doMatch(a, aProject));
        assertTrue(ProjectDeps.doMatch(aProject, a));

        assertTrue(ProjectDeps.doMatch(a, aPlugin));
        assertTrue(ProjectDeps.doMatch(aPlugin, a));

        assertFalse(ProjectDeps.doMatch(aProject, aPlugin));
        assertFalse(ProjectDeps.doMatch(aPlugin, aProject));
    }
}
