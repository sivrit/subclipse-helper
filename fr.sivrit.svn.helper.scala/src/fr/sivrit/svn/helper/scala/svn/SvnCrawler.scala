package fr.sivrit.svn.helper.scala.svn

import fr.sivrit.svn.helper.scala.core.ProjectDeps
import fr.sivrit.svn.helper.Preferences
import org.tigris.subversion.svnclientadapter.{ ISVNDirEntry, ISVNInfo, SVNNodeKind, SVNUrl }
import scala.actors.Actor
import scala.io.Source
import scala.util.matching.Regex
import scala.xml.Elem
import scala.xml.XML
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.core.runtime.IProgressMonitor
import scala.actors.TIMEOUT

class SvnCrawler(monitor: SubMonitor, useExclusions: Boolean) {
  import scala.actors.Actor._

  val debug: Boolean = "true".equalsIgnoreCase(System.getProperty("SvnCrawler.debug"))
  private def trace(msg: String) = if (debug) System.err.println(msg)

  val client = SvnClient.createClient();

  val exclusions: Array[Regex] = if (useExclusions) Preferences.getExclusions.map { pattern => pattern.r } else Array[Regex]()
  val requireNature = Preferences.ignoreNaturelessProjects

  var excludedUrls = Set[SVNUrl]()
  var alreadyChecked = Set[SVNUrl]()

  def findProjects(urls: Array[SVNUrl]): Set[(ProjectDeps, SVNUrl)] =
    parallelFindProjects(urls)

  private sealed case class Interrupted()
  private sealed case class Result(result: Set[(ProjectDeps, SVNUrl)])

  private def parallelFindProjects(urls: Array[SVNUrl]): Set[(ProjectDeps, SVNUrl)] = {
    val mainSelf = self;

    actor {
      val controlerSelf = self;
      assert(mainSelf != controlerSelf)

      var result = Set[(ProjectDeps, SVNUrl)]()

      var todo = for (url <- urls.toList) yield (url, null.asInstanceOf[SvnFolderEntry])
      var liveActors = 0
      var totalWork = todo.size
      var processed = 0

      trace("Base urls: " + todo)

      monitor.setWorkRemaining(IProgressMonitor.UNKNOWN);
      val maxThreads = Preferences.getMaxCrawlerRequests()
      while ((liveActors > 0 || !todo.isEmpty) && !monitor.isCanceled()) {
        todo match {
          case (url, entry) :: more if liveActors < maxThreads => {
            if (isExcluded(url)) {
              excludedUrls += url
            } else if (!alreadyChecked.contains(url)) {
              alreadyChecked += url
              trace("spawn: " + url)
              startCrawler(entry, url, controlerSelf)
              liveActors += 1
            }
            todo = more
          }
          case _ => {
            val timeout = receiveWithin(1000) {
              case Project(p, url) =>
                trace("receive project: " + url); result += ((p, url))
              case Todo(urls) =>
                trace("receive todo: " + urls); totalWork += urls.size; todo ++= urls
              case e: Exception =>
                trace("receive failure: " + e.getMessage); e.printStackTrace()
              case TIMEOUT => TIMEOUT
            }
            if (timeout != TIMEOUT) {
              liveActors -= 1
              processed += 1
            }
          }
        }

        monitor.subTask("[" + liveActors + "] Progress: " + (totalWork - processed) + " out of " + totalWork);
        monitor.worked(1);
      }

      if (monitor.isCanceled()) {
        mainSelf ! Interrupted()
      } else {
        mainSelf ! Result(result)
      }
    }

    return receive {
      case Result(result) => result
      case Interrupted    => throw new InterruptedException()
    }
  }

  private sealed trait CralwerResult
  private case class Todo(entries: List[(SVNUrl, SvnFolderEntry)]) extends CralwerResult
  private case class Project(p: ProjectDeps, url: SVNUrl) extends CralwerResult

  private def startCrawler(entry: SvnFolderEntry, url: SVNUrl, parent: Actor): Unit = {
    actor {
      trace("startCrawler(" + url + ")")
      try {
        val node =
          if (entry == null) {
            client.fetch(url);
          } else {
            assert(entry.isDir, url.toString)
            client.fetch(url, entry.version, entry.isDir);
          }

        if (node == null) throw new IllegalArgumentException("No node for " + url);
        if (!node.isInstanceOf[SvnDir]) throw new IllegalArgumentException(url + " is not a directory!");

        val entries: Set[SvnFolderEntry] = node.asInstanceOf[SvnDir].children
        identifyProject(url, entries) match {
          case Some(project) =>
            trace("startCrawler(" + url + ") => project " + project.name); parent ! Project(project, url)
          case None => {
            trace("startCrawler(" + url + ") => dir ");
            val todo = entries.foldLeft(List.empty[(SVNUrl, SvnFolderEntry)]) { (results, entry) =>
              if (entry.isDir)
                (url.appendPath(entry.name), entry) :: results
              else
                results
            }
            parent ! Todo(todo)
          }
        }
      } catch {
        case e: Exception => trace("startCrawler(" + url + ") => failed: " + e.getMessage); parent ! e
      }
    }
  }

  private def identifyProject(url: SVNUrl, entries: Set[SvnFolderEntry]): Option[ProjectDeps] = {
    entries.find { entry => !entry.isDir && ".project" == entry.name } match {
      case Some(entry) => {
        val projectFileContent = client.fetch(url.appendPath(entry.name), entry.version, entry.isDir)
        val (name, projectDeps, natures) = getProjectInfo(projectFileContent)
        if (requireNature && natures.isEmpty) { None } else {
          val (plugin, pluginDeps) = findPluginInfo(url, entries)
          Some(ProjectDeps(name, plugin, projectDeps, pluginDeps, natures))
        }
      }
      case None => None;
    }
  }

  private def getProjectInfo(projectFile: SvnNode): (String, Set[String], Set[String]) = {
    val description: Elem = XML.loadString(projectFile.asInstanceOf[SvnFile].content)
    (ProjectDeps.findProjectName(description), Set.empty, ProjectDeps.findProjectNatures(description))
  }

  private def findPluginInfo(projectUrl: SVNUrl, entries: Set[SvnFolderEntry]): (String, Set[String]) = {
    entries.find { entry => entry.isDir && "META-INF" == entry.name } match {
      case Some(schemaInfDir) => {
        val metaInfUrl = projectUrl.appendPath(schemaInfDir.name)
        val metaInfEntries: SvnDir = client.fetch(metaInfUrl, schemaInfDir.version, schemaInfDir.isDir).asInstanceOf[SvnDir]
        metaInfEntries.children.find { entry => !entry.isDir && "MANIFEST.MF" == entry.name } match {
          case Some(manifestEntry) =>
            getPluginInfo(client.fetch(metaInfUrl.appendPath(manifestEntry.name), manifestEntry.version, manifestEntry.isDir))
          case None => (null, Set.empty);
        }
      }
      case None => (null, Set.empty);
    }
  }

  private def getPluginInfo(manifestFile: SvnNode): (String, Set[String]) = {
    val content = manifestFile.asInstanceOf[SvnFile].content
    (ProjectDeps.findPluginName(Source.fromString(content)), ProjectDeps.findPluginDeps(Source.fromString(content)))
  }

  private def isExcluded(url: SVNUrl): Boolean = {
    val urlString = url.toString
    exclusions.exists { regex => regex.pattern.matcher(urlString).matches }
  }
}