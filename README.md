## Purpose
I work an a big project composed of _many_ OSGi plug-ins, each being its own Eclipse project. They are versioned under Subversion using a conventional scheme like http://server/repository/trunk/subfolder/pluginId. Subclipse works well enough, but managing all those projects, assigning them into working sets and switching from branch to branch can get quite tedious.

To make some of these tasks easier, I set off to implements meta-operations for [Subclipse](http://subclipse.tigris.org/). The goal is to have plug-ins that will locate Eclipse projects in the repository and use informations from the .project file and plug-in definition file to execute the necessary Subclipse commands.

On the way, I also decided to experiment with Scala. Being unfamiliar with Scala, and to attempt a comparison, I defined a extension point providing multiple implementations (one in Scala, one in Java). The host plug-in and all the UI are done in Java to benefit from the usual tools.

## Usage
 - If necessagy, get [Subclipse](http://subclipse.tigris.org/).
 - Add all plug-ins to your Eclipse installation (in the "dropins" folder)

This will contribute the following operations to the "SVN Repositories" view:

 - _Recursive switch/checkout_. This will explore recursively each selected directory. For each Eclipse project found, it will either be checked out, or, if there is a project in the workspace with the same project name or plug-in id, switched to.
 - _Define working sets_. A working set will be created for each selected folder. All projects found inside these folders in the repository will be assigned to the corresponding working set.

This should work with Eclipse versions from 3.5 to 3.7.

## License
Licensed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html).

## Disclaimers
The usual: I am writing this to take care of an itch of mine. This is published in case this could be of some use to someone else, but I make no promises.

## Dependencies
 - Subclipse 1.6.
 - For the unit tests of the scala plug-in, [ScalaTest](http://www.scalatest.org/) should be registered as a user library under the name "scalatest-1.4", but it is not needed at runtime.
 - A plug-in containing the Scala runtime 2.9. This could be provided either by [Scala IDE](http://www.scala-ide.org/) or the scala-library jar.

## TODO
 - Add some more tests (tricky since it will require to mock Subclipse and Eclipse APIs).
 - Have the scala version catch up with the java one.

