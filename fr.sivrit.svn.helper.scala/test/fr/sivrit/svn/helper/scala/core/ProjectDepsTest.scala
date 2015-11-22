package fr.sivrit.svn.helper.scala.core

import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.scalatest.Assertions._
import scala.io.Source

class ProjectDepsTest extends JUnitSuite {
  val sampleProjectFile =
    <projectDescription>
      <name>ProjectName</name>
      <comment/>
      <projects><project>referenced.project</project></projects>
      <buildSpec>
        <buildCommand>
          <name>org.eclipse.pde.ManifestBuilder</name>
          <arguments></arguments>
        </buildCommand>
        <buildCommand>
          <name>org.eclipse.pde.SchemaBuilder</name>
          <arguments>
          </arguments>
        </buildCommand>
        <buildCommand>
          <name>org.scala-ide.sdt.core.scalabuilder</name>
          <arguments>
          </arguments>
        </buildCommand>
      </buildSpec>
      <natures>
        <nature>org.scala-ide.sdt.core.scalanature</nature>
        <nature>org.eclipse.jdt.core.javanature</nature>
        <nature>org.eclipse.pde.PluginNature</nature>
      </natures>
    </projectDescription>

  val projectFileWithNoNature =
    <projectDescription>
      <name>fr.some.thing</name>
      <comment></comment>
      <projects> </projects>
      <buildSpec>
      </buildSpec>
      <natures>
      </natures>
    </projectDescription>

  val sampleCompleteManifest = """Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: BundleName
Bundle-SymbolicName: bundle.symbolic.name;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: fr.sivrit.svn.helper.java.ActivatorJava
Require-Bundle: org.eclipse.core.runtime,
 org.eclipse.core.resources;bundle-version="1.0.0",
 org.eclipse.core.stuff,
 org.eclipse.core.more.stuff;bundle-version="1.0.0"
Bundle-ActivationPolicy: lazy
Bundle-RequiredExecutionEnvironment: JavaSE-1.6"""

  val sampleSimpleManifest = """Manifest-Version: 1.0
Bundle-Name: BundleName
Require-Bundle: org.eclipse.core.runtime
Bundle-SymbolicName: bundle.symbolic.name
"""

  val sampleTrickyManifestA = """Manifest-Version: 1.0
Bundle-Name: BundleName
Bundle-SymbolicName: bundle.symbolic.name;singleton:=false
Require-Bundle: org.eclipse.core.runtime;bundle-version="1.0.0"
"""

  val sampleTrickyManifestB = """Manifest-Version: 1.0
Bundle-Name: BundleName
Require-Bundle: org.eclipse.core.runtime;bundle-version="1.0.0",
 org.eclipse.core.resources
Bundle-SymbolicName: bundle.symbolic.name;singleton:=false
"""

  val sampleNoDepManifest = """Manifest-Version: 1.0
Bundle-Name: BundleName
Bundle-SymbolicName: bundle.symbolic.name
"""

  @Test
  def verifyFindProjectName() {
    assertEquals("ProjectName", ProjectDeps.findProjectName(sampleProjectFile))
  }

  @Test
  def verifyEmptyProjectNature() {
    assertResult(Set())(ProjectDeps.findProjectNatures(projectFileWithNoNature))
  }

  @Test
  def verifyProjectNatures() {
    assertEquals(Set("org.scala-ide.sdt.core.scalanature", "org.eclipse.jdt.core.javanature", "org.eclipse.pde.PluginNature"), ProjectDeps.findProjectNatures(sampleProjectFile))
  }

  @Test
  def verifyFindPluginName() {
    assertEquals("bundle.symbolic.name", ProjectDeps.findPluginName(Source.fromString(sampleCompleteManifest)))
    assertEquals("bundle.symbolic.name", ProjectDeps.findPluginName(Source.fromString(sampleSimpleManifest)))
    assertEquals("bundle.symbolic.name", ProjectDeps.findPluginName(Source.fromString(sampleTrickyManifestA)))
    assertEquals("bundle.symbolic.name", ProjectDeps.findPluginName(Source.fromString(sampleTrickyManifestB)))
    assertEquals("bundle.symbolic.name", ProjectDeps.findPluginName(Source.fromString(sampleNoDepManifest)))
  }

  @Test
  def verifyCompleteFindPluginDepsComplete() {
    val deps = ProjectDeps.findPluginDeps(Source.fromString(sampleCompleteManifest))
    assertEquals(Set("org.eclipse.core.runtime", "org.eclipse.core.resources", "org.eclipse.core.stuff", "org.eclipse.core.more.stuff"), deps)
  }

  @Test
  def verifySimpleFindPluginDepsComplete() {
    val deps = ProjectDeps.findPluginDeps(Source.fromString(sampleSimpleManifest))
    assertEquals(Set("org.eclipse.core.runtime"), deps)
  }

  @Test
  def verifyTrikyAFindPluginDepsComplete() {
    val deps = ProjectDeps.findPluginDeps(Source.fromString(sampleTrickyManifestA))
    assertEquals(Set("org.eclipse.core.runtime"), deps)
  }

  @Test
  def verifyTrikyBFindPluginDepsComplete() {
    val deps = ProjectDeps.findPluginDeps(Source.fromString(sampleTrickyManifestB))
    assertEquals(Set("org.eclipse.core.runtime", "org.eclipse.core.resources"), deps)
  }

  @Test
  def verifyNoDepFindPluginDepsComplete() {
    val deps = ProjectDeps.findPluginDeps(Source.fromString(sampleNoDepManifest))
    assertEquals(Set(), deps)
  }

  @Test
  def verifyDoMatch() {
    val a = new ProjectDeps("projectA", "pluginA", null, null)
    val b = new ProjectDeps("projectB", "pluginB", null, null)
    val aProject = new ProjectDeps("projectA", null, null, null)
    val aPlugin = new ProjectDeps(null, "pluginA", null, null)

    assertTrue(ProjectDeps.doMatch(a, a))

    assertFalse(ProjectDeps.doMatch(a, b))
    assertFalse(ProjectDeps.doMatch(b, a))

    assertTrue(ProjectDeps.doMatch(a, aProject))
    assertTrue(ProjectDeps.doMatch(aProject, a))

    assertTrue(ProjectDeps.doMatch(a, aPlugin))
    assertTrue(ProjectDeps.doMatch(aPlugin, a))

    assertFalse(ProjectDeps.doMatch(aProject, aPlugin))
    assertFalse(ProjectDeps.doMatch(aPlugin, aProject))
  }
}
