package com.xpn.xwiki.tool.xar;

import java.io.File;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

/**
 * Base class for xar and unxar mojos.
 * 
 * @version $Id$
 */
abstract class AbstractXarMojo extends AbstractMojo
{
    /**
     * Open hook.
     */
    protected static final String HOOK_OPEN = "[";

    /**
     * Close hook.
     */
    protected static final String HOOK_CLOSE = "]";

    /**
     * The name of the file in the package when to find general informations.
     */
    protected static final String PACKAGE_XML = "package.xml";

    /**
     * The name of the tag that marks the list of files in PACKAGE_XML.
     */
    protected static final String FILES_TAG = "files";

    /**
     * The name of the tag that marks a specific file in PACKAGE_XML.
     */
    protected static final String FILE_TAG = "file";

    /** Default encoding to use. */
    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Default excludes.
     * 
     * @todo For now we exclude all files in META-INF even though we would like to keep them. This is because we want
     *       that newly generated XAR be compatible with older versions of XWiki (as otherwise they wouldn't be able to
     *       be imported in those older versions as the Package plugin would fail.
     */
    private static final String[] DEFAULT_EXCLUDES = new String[] {"**/META-INF/**"};

    /**
     * Default includes.
     */
    private static final String[] DEFAULT_INCLUDES = new String[] {"**/**"};

    /**
     * List of files to include. Specified as fileset patterns.
     * 
     * @parameter
     */
    protected String[] includes;

    /**
     * List of files to exclude. Specified as fileset patterns.
     * 
     * @parameter
     */
    protected String[] excludes;

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The encoding to use when generating the package summary file and when storing file names.
     * 
     * @parameter expression="${project.build.sourceEncoding}"
     */
    protected String encoding = DEFAULT_ENCODING;

    /**
     * @return the includes
     */
    protected String[] getIncludes()
    {
        if (this.includes != null && this.includes.length > 0) {
            return this.includes;
        }

        return DEFAULT_INCLUDES;
    }

    /**
     * @return the excludes
     */
    protected String[] getExcludes()
    {
        if (this.excludes != null && this.excludes.length > 0) {
            return this.excludes;
        }

        return DEFAULT_EXCLUDES;
    }

    /**
     * Unpacks the XAR file (exclude the package.xml file if it exists).
     * 
     * @param file the file to be unpacked.
     * @param location the location where to put the unpacket files.
     * @param logName the name use with {@link ConsoleLogger}.
     * @param overwrite indicate if extracted files has to overwrite existing ones.
     * @throws MojoExecutionException error when unpacking the file.
     */
    protected void unpack(File file, File location, String logName, boolean overwrite) throws MojoExecutionException
    {
        try {
            ZipUnArchiver unArchiver = new ZipUnArchiver();
            unArchiver.setEncoding(this.encoding);
            unArchiver.enableLogging(new ConsoleLogger(Logger.LEVEL_ERROR, logName));
            unArchiver.setSourceFile(file);
            unArchiver.setDestDirectory(location);

            FileSelector[] selectors;

            IncludeExcludeFileSelector fs = new IncludeExcludeFileSelector();
            fs.setIncludes(getIncludes());
            fs.setExcludes(getExcludes());

            // Ensure that we don't overwrite XML document files present in this project since
            // we want those to be used and not the ones in the dependent XAR.
            unArchiver.setOverwrite(overwrite);

            if (!overwrite) {
                // Do not unpack any package.xml file in dependant XARs. We'll generate a complete
                // one automatically.
                IncludeExcludeFileSelector fs2 = new IncludeExcludeFileSelector();
                fs2.setExcludes(new String[] {PACKAGE_XML});
                selectors = new FileSelector[] {fs, fs2};
            } else {
                selectors = new FileSelector[] {fs};
            }

            unArchiver.setFileSelectors(selectors);

            unArchiver.extract();
        } catch (Exception e) {
            throw new MojoExecutionException("Error unpacking file " + HOOK_OPEN + file + HOOK_CLOSE + " to "
                + HOOK_OPEN + location + HOOK_CLOSE, e);
        }
    }

    /**
     * Unpacks A XAR artifacts into the build output directory, along with the project's XAR files.
     * 
     * @param artifact the XAR artifact to unpack.
     * @throws MojoExecutionException in case of unpack error
     */
    protected void unpackXarToOutputDirectory(Artifact artifact) throws MojoExecutionException
    {
        File outputLocation = new File(this.project.getBuild().getOutputDirectory());

        if (!outputLocation.exists()) {
            outputLocation.mkdirs();
        }

        File file = artifact.getFile();
        unpack(file, outputLocation, "XarMojo", false);
    }

    /**
     * Unpack xar dependencies before pack then into it.
     * 
     * @throws MojoExecutionException error when unpack dependencies.
     */
    protected void unpackDependentXars() throws MojoExecutionException
    {
        Set<Artifact> artifacts = this.project.getArtifacts();
        for (Artifact artifact : artifacts) {
            ScopeArtifactFilter filter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
            if (!artifact.isOptional() && filter.include(artifact)) {
                String type = artifact.getType();
                if ("xar".equals(type)) {
                    unpackXarToOutputDirectory(artifact);
                }
            }
        }
    }
}
