package fr.sivrit.svn.helper.scala.svn
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.Writer
import org.tigris.subversion.svnclientadapter.SVNUrl
import java.io.IOException
import fr.sivrit.svn.helper.Logger
import java.io.File
import java.nio.charset.Charset
import org.eclipse.core.runtime.IStatus
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.FileInputStream
import fr.sivrit.svn.helper.scala.SvnHelperScala
import scala.io.Source

class CachedSvnClient(cacheDir: String) extends SvnClient {
  val DIR = "DIR"
  val FILE = "FILE"

  private def getPath(url: SVNUrl, version: Long): File = {
    val host = url.getHost
    val port = Integer.toString(url.getPort)

    var path = cacheDir
    path += host + '$' + port + File.separator
    path += version + File.separator

    for (segment <- url.getPathSegments) {
      path += '$' + segment
    }

    new File(path)
  }

  override def fetch(url: SVNUrl, version: Long, isDir: Boolean): SvnNode = {
    val file: File = getPath(url, version)
    if (file.exists) {
      load(url, version, file)
    } else {
      val result: SvnNode = super.fetch(url, version, isDir)
      store(result, version)

      result
    }
  }

  private def store(entry: SvnNode, version: Long) = {
    synchronized {
      val file: File = getPath(entry.url, version)
      new File(file.getParent()).mkdirs()

      var writer: Writer = null
      try {
        if (file.createNewFile()) {
          writer = new OutputStreamWriter(new FileOutputStream(file),
            Charset.forName("UTF-8"));
          entry match {
            case entry: SvnDir =>
              writer.write(DIR)
              writer.write('\n')
              for (item: SvnFolderEntry <- entry.children) {
                writer.write(if (item.isDir) DIR else FILE)
                writer.write('$')
                writer.write(item.version.toString)
                writer.write('$')
                writer.write(item.name)
                writer.write('\n')
              };
            case entry: SvnFile =>
              writer.write(FILE);
              writer.write('\n');
              writer.write(entry.content);
          }
        }
      } catch {
        case e: IOException =>
          Logger.log(IStatus.ERROR, SvnHelperScala.PLUGIN_ID, e);
      } finally {
        try {
          if (writer != null) {
            writer.close();
          } else {
            file.delete();
          }
        } catch {
          case e: IOException =>
            Logger.log(IStatus.ERROR, SvnHelperScala.PLUGIN_ID, e);
        }
      }
    }
  }

  private def load(url: SVNUrl, version: Long, file: File): SvnNode = {
    synchronized {
      val source = Source.fromFile(file, "UTF-8")
      try {
        val lines = source.getLines()
        lines.next() match {
          case DIR => loadDir(url, version, lines)
          case FILE =>
            loadFile(url, version, lines)
          case typ =>
            throw new IllegalStateException(typ + " read in " + file.toString());
        }
      } catch {
        case e: IOException =>
          Logger.log(IStatus.ERROR, SvnHelperScala.PLUGIN_ID, e);
          return null;
      } finally {
        try {
          source.close();
        } catch {
          case e: IOException =>
            Logger.log(IStatus.ERROR, SvnHelperScala.PLUGIN_ID, e);
        }
      }
    }
  }

  private def loadDir(url: SVNUrl, version: Long, lines: Iterator[String]): SvnNode = {
    val entries =
      for (
        line <- lines;

        sep1 = line.indexOf('$');
        typ = line.substring(0, sep1);

        versionAndName = line.substring(sep1 + 1);
        sep2 = versionAndName.indexOf('$');
        entryVersion: Long = versionAndName.substring(0, sep2).toLong;
        name: String = versionAndName.substring(sep2 + 1)
      ) yield new SvnFolderEntry(name, entryVersion, DIR == typ)

    SvnDir(url, version, entries.toSet)
  }

  private def loadFile(url: SVNUrl, version: Long, lines: Iterator[String]): SvnNode = {
    val content = lines.foldLeft("") { (base, line) => base + line + '\n' }
    SvnFile(url, version, content.toString)
  }
}