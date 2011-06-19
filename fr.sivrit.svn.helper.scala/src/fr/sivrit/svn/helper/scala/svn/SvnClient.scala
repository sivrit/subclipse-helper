package fr.sivrit.svn.helper.scala.svn

import java.io.InputStream
import org.tigris.subversion.subclipse.core.SVNProviderPlugin
import org.tigris.subversion.svnclientadapter.{ISVNClientAdapter, ISVNDirEntry, ISVNInfo, SVNRevision, SVNUrl}
import scala.io.Source
import scala.xml.{Elem, XML}

class SvnClient(adapter: ISVNClientAdapter) {

  def adapter(): ISVNClientAdapter = adapter;

  def getList(svnUrl: SVNUrl): Array[ISVNDirEntry] = {
    return adapter.getList(svnUrl, SVNRevision.HEAD, false)
  }

  def getInfo(svnUrl: SVNUrl): ISVNInfo = {
    return adapter.getInfo(svnUrl)
  }

  def getContent(svnUrl: SVNUrl): InputStream = {
    adapter.getContent(svnUrl, SVNRevision.HEAD)
  }

  def getStringContent(svnUrl: SVNUrl): String = {
    Source.fromInputStream(getContent(svnUrl)).mkString
  }

  def getXMLContent(svnUrl: SVNUrl): Elem = {
    XML.load(getContent(svnUrl))
  }
}

object SvnClient {
  def getSvnClient(): SvnClient = {
    val adapter: ISVNClientAdapter = SVNProviderPlugin.getPlugin().getSVNClient()
    new SvnClient(adapter)
  }
}