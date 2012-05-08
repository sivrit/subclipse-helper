package fr.sivrit.svn.helper.scala.svn

import java.io.InputStream
import org.tigris.subversion.subclipse.core.SVNProviderPlugin
import org.tigris.subversion.svnclientadapter.{ ISVNClientAdapter, ISVNDirEntry, ISVNInfo, SVNRevision, SVNUrl }
import scala.io.Source
import scala.xml.{ Elem, XML }
import org.tigris.subversion.svnclientadapter.ISVNStatus
import java.io.File
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.BlockingDeque

class SvnAdapter(adapter: ISVNClientAdapter) {

  def adapter(): ISVNClientAdapter = adapter;

  def getList(svnUrl: SVNUrl): Array[ISVNDirEntry] = {
    return adapter.getList(svnUrl, SVNRevision.HEAD, false)
  }

  def getInfo(svnUrl: SVNUrl): ISVNInfo = {
    return adapter.getInfo(svnUrl)
  }

  def getStatus(file: File): ISVNStatus = {
    adapter.getSingleStatus(file)
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

object SvnAdapter {
  private val pool: BlockingDeque[SvnAdapter] = new LinkedBlockingDeque[SvnAdapter]();

  def borrow(): SvnAdapter = {
    val adapter: SvnAdapter = pool.pollLast()
    if (adapter == null) {
      new SvnAdapter(SVNProviderPlugin.getPlugin.getSVNClient)
    } else {
      adapter
    }
  }

  def release(adapter: SvnAdapter) {
    assert(adapter != null)
    pool.offerLast(adapter)
  }

  def clearPool() = pool.clear()

  def withSvnAdapter(op: SvnAdapter => Unit) {
    val adapter = borrow()
    try {
      op(adapter)
    } finally {
      release(adapter)
    }
  }

}