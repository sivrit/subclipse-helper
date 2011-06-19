package fr.sivrit.svn.helper.scala.svn

import fr.sivrit.svn.helper.scala.core.ProjectDeps
import fr.sivrit.svn.helper.Preferences
import org.tigris.subversion.svnclientadapter.{ ISVNDirEntry, ISVNInfo, SVNNodeKind, SVNUrl }
import scala.actors.Actor
import scala.io.Source
import scala.util.matching.Regex
import scala.xml.Elem

object SvnCrawler {
  import scala.actors.Actor._

  val debug: Boolean = "true".equalsIgnoreCase(System.getProperty("SvnCrawler.debug"))

  private def trace(msg: String) = if (debug) System.err.println(msg)

  def findProjects(urls: Array[SVNUrl], exclusions: List[Regex]): Set[(ProjectDeps, SVNUrl)] =
    parallelFindProjects(urls, exclusions)

  private def parallelFindProjects(urls: Array[SVNUrl], exclusions: List[Regex]): Set[(ProjectDeps, SVNUrl)] = {
    var result = Set[(ProjectDeps, SVNUrl)]()

    var excludedUrls = Set[SVNUrl]()
    var badUrls = Set[SVNUrl]()
    var alreadyChecked = Set[SVNUrl]()

    var todo = urls.toList
    var liveActors = 0

    trace("Base urls: " + todo)

    val maxThreads = Preferences.getMaxCrawlerRequests()
    while (liveActors > 0 || !todo.isEmpty) {
      todo match {
        case url :: more if liveActors < maxThreads => {
          if (isExcluded(url, exclusions)) {
            excludedUrls += url
          } else if (!isDir(url)) {
            badUrls += url
          } else if (!alreadyChecked.contains(url)) {
            alreadyChecked += url
            trace("spawn: " + url)
            startCrawler(url, SvnClient.getSvnClient, self)
            liveActors += 1
          }
          todo = more
        }
        case _ => {
          receive {
            case item: (ProjectDeps, SVNUrl) => trace("receive project: " + item._2); result = result + item
            case urls: List[SVNUrl] => trace("receive todo: " + urls); todo = urls ++ todo
            case e: Exception => trace("receive failure: " + e.getMessage); e.printStackTrace()
          }
          liveActors -= 1
        }
      }
    }
    result
  }

  private def startCrawler(url: SVNUrl, svn: SvnClient, parent: Actor): Unit = {
    actor {
      trace("startCrawler(" + url + ")")
      try {
        val entries: Array[ISVNDirEntry] = svn.getList(url)
        identifyProject(url, entries, svn) match {
          case Some(project) => trace("startCrawler(" + url + ") => project " + project.name); parent ! (project, url)
          case None =>
            trace("startCrawler(" + url + ") => dir "); parent ! entries.foldLeft(List.empty[SVNUrl]) { (results, entry) =>
              if (entry.getNodeKind == SVNNodeKind.DIR)
                url.appendPath(entry.getPath) :: results
              else
                results
            }
        }
      } catch {
        case e: Exception => trace("startCrawler(" + url + ") => failed: " + e.getMessage); parent ! e
      }
    }
  }

  private def identifyProject(url: SVNUrl, entries: Array[ISVNDirEntry], svn: SvnClient): Option[ProjectDeps] = {
    entries.find { entry => entry.getNodeKind == SVNNodeKind.FILE && ".project".equals(entry.getPath()) } match {
      case Some(entry) => {
        val (name, projectDeps) = getProjectInfo(url.appendPath(entry.getPath), svn)
        val (plugin, pluginDeps) = findPluginInfo(url, svn, entries)
        Some(new ProjectDeps(name, plugin, projectDeps, pluginDeps))
      }
      case None => None;
    }
  }

  private def getProjectInfo(projectFile: SVNUrl, svn: SvnClient): (String, Set[String]) = {
    val description: Elem = svn.getXMLContent(projectFile)
    (ProjectDeps.findProjectName(description), Set.empty)
  }

  private def findPluginInfo(projectUrl: SVNUrl, svn: SvnClient, entries: Array[ISVNDirEntry]): (String, Set[String]) = {
    entries.find { entry => entry.getNodeKind == SVNNodeKind.DIR && "META-INF".equals(entry.getPath) } match {
      case Some(schemaInfDir) => {
        val metaInfUrl = projectUrl.appendPath(schemaInfDir.getPath)
        val metaInfEntries = svn.getList(metaInfUrl)
        metaInfEntries.find { entry => entry.getNodeKind == SVNNodeKind.FILE && "MANIFEST.MF".equals(entry.getPath) } match {
          case Some(manifestEntry) => getPluginInfo(metaInfUrl.appendPath(manifestEntry.getPath), svn)
          case None => (null, Set.empty);
        }
      }
      case None => (null, Set.empty);
    }
  }

  private def getPluginInfo(manifestFile: SVNUrl, svn: SvnClient): (String, Set[String]) = {
    val content = svn.getStringContent(manifestFile)
    (ProjectDeps.findPluginName(Source.fromString(content)), ProjectDeps.findPluginDeps(Source.fromString(content)))
  }

  private def isExcluded(url: SVNUrl, exclusions: List[Regex]): Boolean = {
    val urlString = url.toString
    exclusions.exists { regex => regex.pattern.matcher(urlString).matches }
  }

  private def isDir(url: SVNUrl): Boolean = {
    val svn = SvnClient.getSvnClient
    val info: ISVNInfo = svn.getInfo(url)
    info.getNodeKind == SVNNodeKind.DIR
  }
}