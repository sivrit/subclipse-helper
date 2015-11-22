package fr.sivrit.svn.helper.java.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.SubMonitor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import fr.sivrit.svn.helper.java.repo.ProjectDeps;

public class ProjectUtils {
    private ProjectUtils() {
        super();
    }

    public static IProject findWorkspaceProject(final String name) {
        return ResourcesPlugin.getWorkspace().getRoot().getProject(name);
    }

    private final static String BUNDLE_NAME = "Bundle-SymbolicName:";
    private final static String BUNDLE_DEPENDENCIES = "Require-Bundle:";

    // Sample manifest :
    // Manifest-Version: 1.0
    // Bundle-ManifestVersion: 2
    // Bundle-Name: SvnHelperImpl
    // Bundle-SymbolicName: SvnHelperScalaImpl;singleton:=true
    // Bundle-Version: 1.0.0.qualifier
    // Require-Bundle: org.eclipse.core.resources;bundle-version="3.5.2",
    // org.eclipse.core.runtime;bundle-version="3.5.0",
    // org.eclipse.ui.workbench;bundle-version="3.5.2",
    // org.eclipse.osgi.services;bundle-version="3.2.0",
    // org.eclipse.jface;bundle-version="3.5.2"
    // Bundle-ActivationPolicy: lazy
    // Bundle-RequiredExecutionEnvironment: JavaSE-1.6
    // Export-Package: svnhelper.core.repo
    // Bundle-Activator: svnhelper.Activator

    public static Collection<String> splitLines(final String content) {
        return Arrays.asList(content.split("\n"));
    }

    public static void fillFromManifest(final ProjectDeps project, final File manifest) {
        final Collection<String> lines = new ArrayList<String>();
        try {
            final FileInputStream fstream = new FileInputStream(manifest);
            final DataInputStream in = new DataInputStream(fstream);
            final BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            while ((strLine = br.readLine()) != null) {
                lines.add(strLine);
            }
            // Close the input stream
            in.close();
        } catch (final FileNotFoundException e) {// Catch exception if any
            throw new IllegalArgumentException("File " + manifest + " could not be read", e);
        } catch (final IOException e) {
            throw new IllegalArgumentException("File " + manifest + " could not be read", e);
        }

        fillFromManifest(project, lines);
    }

    public static void fillFromManifest(final ProjectDeps project, final Collection<String> manifest) {
        boolean inDependencies = false;
        for (final String line : manifest) {
            if (line.startsWith(BUNDLE_NAME)) {
                assert !inDependencies;
                final String name = line.substring(BUNDLE_NAME.length()).replaceAll(";.*", "")
                        .trim();
                if (project.getPlugin() != null) {
                    assert project.getPlugin().equals(name);
                }
                project.setPlugin(name);
            } else if (line.startsWith(BUNDLE_DEPENDENCIES)) {
                assert !inDependencies;
                final int start = line.indexOf(':') + 1;
                final int end = line.contains(";") ? line.indexOf(';') : line.length();
                final String plugin = line.substring(start, end).replaceAll(",", "").trim();

                assert !project.getPluginDeps().contains(plugin);
                project.getPluginDeps().add(plugin);
                inDependencies = line.endsWith(",");
            } else if (inDependencies) {
                final int end = line.indexOf(';');
                final String pluginRaw = end == -1 ? line.replaceAll(",", "") : line.substring(0,
                        end);
                final String plugin = pluginRaw.trim();

                assert !project.getPluginDeps().contains(plugin);
                project.getPluginDeps().add(plugin);
                inDependencies = line.endsWith(",");
            }
        }
    }

    public static Collection<ProjectDeps> findWorkspaceProjects(final SubMonitor subMonitor) {
        final Collection<ProjectDeps> result = new ArrayList<ProjectDeps>();
        final IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        subMonitor.setWorkRemaining(projects.length);
        for (final IProject project : projects) {
            final ProjectDeps deps = new ProjectDeps(project.getName(), null);

            final IFile manifestFile = project.getFile("META-INF/MANIFEST.MF");
            final IPath manifestPath = manifestFile == null ? null : manifestFile.getLocation();
            final File manifest = manifestPath == null ? null : manifestPath.toFile();
            if (manifest != null && manifest.exists() && manifest.isFile()) {
                fillFromManifest(deps, manifest);
            }

            result.add(deps);
            subMonitor.worked(1);
        }

        return result;
    }

    public static String findPluginName(final String manifestContent) {
        return findPluginName(splitLines(manifestContent));
    }

    private static String findPluginName(final Collection<String> manifest) {
        final String symbolicNameRegex = "Bundle-SymbolicName:\\s*([^;]+)(?:.*)";
        final Pattern pattern = Pattern.compile(symbolicNameRegex);

        for (final String line : manifest) {
            final Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                assert matcher.groupCount() > 0;
                return matcher.group(1);
            }
        }

        return null;
    }

    private static InputStream toInputStream(final String fileContent) {
        try {
            return new ByteArrayInputStream(fileContent.getBytes("UTF-8"));
        } catch (final UnsupportedEncodingException e) {
            // Really unexpected
            throw new RuntimeException(e);
        }
    }

    public static String findProjectName(final String projectFileContent) throws IOException {
        return findProjectName(toInputStream(projectFileContent));
    }

    public static Set<String> findProjectNatures(final String projectFileContent)
            throws IOException {
        return findProjectNatures(toInputStream(projectFileContent));
    }

    private static Node findNode(final NodeList nodes, final String name) {
        final int count = nodes.getLength();
        for (int i = 0; i < count; i++) {
            final Node node = nodes.item(i);
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static Document parseXml(final InputStream projectFileContent) throws IOException {
        final Document dom;
        try {
            final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            final DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(projectFileContent);

        } catch (final ParserConfigurationException pce) {
            throw new IllegalStateException(pce);
        } catch (final SAXException se) {
            throw new IllegalArgumentException(se);
        }
        return dom;
    }

    public static String findProjectName(final InputStream projectFileContent) throws IOException {
        final Document dom = parseXml(projectFileContent);

        final Node projectDescription = findNode(dom.getChildNodes(), "projectDescription");
        assert projectDescription != null;

        final Node name = findNode(projectDescription.getChildNodes(), "name");
        assert name != null;

        return name.getTextContent();
    }

    public static Set<String> findProjectNatures(final InputStream projectFileContent)
            throws IOException {
        final Document dom = parseXml(projectFileContent);

        final Node projectDescription = findNode(dom.getChildNodes(), "projectDescription");
        assert projectDescription != null;

        final Node natures = findNode(projectDescription.getChildNodes(), "natures");
        assert natures != null;

        final Set<String> result = new HashSet<String>();

        final NodeList natureList = natures.getChildNodes();
        final int count = natureList.getLength();
        for (int i = 0; i < count; i++) {
            final Node nature = natureList.item(i);
            if ("nature".equals(nature.getNodeName())) {
                result.add(nature.getTextContent());
            }
        }

        return result;
    }
}
