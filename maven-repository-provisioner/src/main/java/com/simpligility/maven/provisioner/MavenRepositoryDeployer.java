/** 
 * Copyright simpligility technologies inc. http://www.simpligility.com
 * Licensed under Eclipse Public License - v 1.0 http://www.eclipse.org/legal/epl-v10.html
 */
package com.simpligility.maven.provisioner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.LoggerFactory;

import com.simpligility.maven.Gav;
import com.simpligility.maven.GavUtil;
import com.simpligility.maven.MavenConstants;

import org.slf4j.Logger;

public class MavenRepositoryDeployer
{
    private static Logger logger = LoggerFactory.getLogger( "MavenRepositoryDeployer" );

    private File repositoryPath;

    private RepositorySystem system;

    private DefaultRepositorySystemSession session;

    private final TreeSet<String> successfulDeploys = new TreeSet<String>();

    private final TreeSet<String> failedDeploys = new TreeSet<String>();

    private final TreeSet<String> skippedDeploys = new TreeSet<String>();
    
    private final TreeSet<String> potentialDeploys = new TreeSet<String>();

    public MavenRepositoryDeployer( File repositoryPath )
    {
        this.repositoryPath = repositoryPath;
        initialize();
    }
    
    private void initialize()
    {
        system = RepositoryHandler.getRepositorySystem();
        session = RepositoryHandler.getRepositorySystemSession( system, repositoryPath );
    }
    
    public static Collection<File> getLeafDirectories( File repoPath ) 
    {
        // Using commons-io, if performance or so is a problem it might be worth looking at the Java 8 streams API
        // e.g. http://blog.jooq.org/2014/01/24/java-8-friday-goodies-the-new-new-io-apis/
        // not yet though..
       Collection<File> subDirectories =
            FileUtils.listFilesAndDirs( repoPath, DirectoryFileFilter.DIRECTORY,
                VisibleDirectoryFileFilter.DIRECTORY );
        Collection<File> leafDirectories = new ArrayList<File>();
        for ( File subDirectory : subDirectories )
        {
            if ( isLeafVersionDirectory( subDirectory ) && subDirectory != repoPath )
            {
                leafDirectories.add( subDirectory );
            }
        }
        return leafDirectories;
    }
    
    /**
     * Determine if it is a leaf directory with artifacts in it. Criteria used is that there is no subdirectory.
     * 
     * @param subDirectory
     * @return
     */
    private static boolean isLeafVersionDirectory( File subDirectory )
    {
        boolean isLeafVersionDirectory;
        Collection<File> subDirectories =
            FileUtils.listFilesAndDirs( subDirectory,
                (IOFileFilter) VisibleDirectoryFileFilter.DIRECTORY,
                (IOFileFilter) VisibleDirectoryFileFilter.DIRECTORY );
        // it finds at least itself so have to check for > 1
        isLeafVersionDirectory = subDirectories.size() > 1 ? false : true; 
        return isLeafVersionDirectory;
    }
    
    public static Collection<File> getPomFiles( File repoPath )
    {
        Collection<File> pomFiles = new ArrayList<File>();
        Collection<File> leafDirectories = getLeafDirectories( repoPath );
        for ( File leafDirectory : leafDirectories )
        {
            IOFileFilter fileFilter = new AndFileFilter( new WildcardFileFilter( "*.pom" ),
                                               new NotFileFilter( new SuffixFileFilter( "sha1" ) ) );
            pomFiles.addAll( FileUtils.listFiles( leafDirectory, fileFilter, null ) );
        }
        return pomFiles;
    }


    public void deployToRemote( String targetUrl, String username, String password, Boolean checkTarget,
        Boolean verifyOnly )
    {
        Collection<File> leafDirectories = getLeafDirectories( repositoryPath );

        for ( File leafDirectory : leafDirectories )
        {
            String leafAbsolutePath = leafDirectory.getAbsoluteFile().toString();
            int repoAbsolutePathLength = repositoryPath.getAbsoluteFile().toString().length();
            String leafRepoPath = leafAbsolutePath.substring( repoAbsolutePathLength + 1, leafAbsolutePath.length() );

            Gav gav = GavUtil.getGavFromRepositoryPath( leafRepoPath );

            boolean pomInTarget = false;
            if ( checkTarget ) 
            {
                pomInTarget = checkIfPomInTarget( targetUrl, gav, username, password );
            }
            
            if ( pomInTarget ) 
            {
                logger.trace( "Found POM for " + gav + " already in target. Skipping deployment." );
                skippedDeploys.add( gav.toString() );
            } 
            else
            {
                // only interested in files using the artifactId-version* pattern
                // don't bother with .sha1 files
                IOFileFilter fileFilter =
                    new AndFileFilter( new WildcardFileFilter( gav.getArtifactId() + "-" + gav.getVersion() + "*" ),
                                       new NotFileFilter( new SuffixFileFilter( "sha1" ) ) );
                Collection<File> artifacts = FileUtils.listFiles( leafDirectory, fileFilter, null );

                Authentication auth = new AuthenticationBuilder().addUsername( username ).addPassword( password )
                                .build();

                RemoteRepository distRepo = new RemoteRepository.Builder( "repositoryIdentifier", "default", targetUrl )
                        .setAuthentication( auth ).build();

                DeployRequest deployRequest = new DeployRequest();
                deployRequest.setRepository( distRepo );
                for ( File file : artifacts )
                {
                    String extension;
                    if ( file.getName().endsWith( "tar.gz" ) )
                    {
                        extension = "tar.gz";
                    }
                    else
                    {
                        extension = FilenameUtils.getExtension( file.getName() );
                    }

                    String baseFileName = gav.getFilenameStart() + "." + extension;
                    String fileName = file.getName();
                    String g = gav.getGroupId();
                    String a = gav.getArtifactId();
                    String v = gav.getVersion();
                    
                    Artifact artifact = null;
                    if ( gav.getPomFilename().equals( fileName ) )
                    {
                        artifact = new DefaultArtifact( g, a, MavenConstants.POM, v );
                    }
                    else if ( gav.getJarFilename().equals( fileName ) )
                    {
                        artifact = new DefaultArtifact( g, a, MavenConstants.JAR, v );
                    }
                    else if ( gav.getSourceFilename().equals( fileName ) )
                    {
                        artifact = new DefaultArtifact( g, a, MavenConstants.SOURCES, MavenConstants.JAR, v );
                    }
                    else if ( gav.getJavadocFilename().equals( fileName ) )
                    {
                        artifact = new DefaultArtifact( g, a, MavenConstants.JAVADOC, MavenConstants.JAR, v );
                    }
                    else if ( gav.getClassesFilename().equals( fileName ) )
                    {
                        artifact = new DefaultArtifact( g, a, MavenConstants.CLASSES, MavenConstants.JAR, v );
                    }
                    else if ( baseFileName.equals( fileName ) )
                    {
                        artifact = new DefaultArtifact( g, a, extension, v );
                    }
                    else
                    {
                        String classifier =
                            file.getName().substring( gav.getFilenameStart().length() + 1,
                                                      file.getName().length() - ( "." + extension ).length() );
                        artifact = new DefaultArtifact( g, a, classifier, extension, v );
                    }

                    if ( artifact != null )
                    {
                        artifact = artifact.setFile( file );
                        deployRequest.addArtifact( artifact );
                    }
                }

                try
                {
                    if ( verifyOnly )
                    {
                      for ( Artifact artifact : deployRequest.getArtifacts() )
                      {
                          potentialDeploys.add( artifact.toString() );
                      }
                    }
                    else
                    {
                      system.deploy( session, deployRequest );
                      for ( Artifact artifact : deployRequest.getArtifacts() )
                      {
                          successfulDeploys.add( artifact.toString() );
                      }
                    }
                }
                catch ( Exception e )
                {
                    logger.trace( "Deployment failed with "
                            + e.getMessage() + ", artifact might be deployed already." );

                    if ( !e.getMessage().endsWith( "(400)" ) )
                    {
                        for ( Artifact artifact : deployRequest.getArtifacts() )
                        {
                            failedDeploys.add( artifact.toString() );
                        }
                    }
                    else
                    {
                        for ( Artifact artifact : deployRequest.getArtifacts() )
                        {
                            skippedDeploys.add( artifact.toString() );
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if POM file for provided gav can be found in target. Just does
     * a HTTP get of the header and verifies http status OK 200.
     * @param targetUrl
     * @param gav
     * @return
     */
    private boolean checkIfPomInTarget( String targetUrl, Gav gav , String username, String password )
    {
        boolean alreadyInTarget = false;
        
        String artifactUrl = targetUrl + gav.getRepositoryURLPath() + gav.getPomFilename();

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials
                = new UsernamePasswordCredentials( username, password );
        provider.setCredentials( AuthScope.ANY, credentials );

        HttpClient httpclient = HttpClientBuilder.create().setDefaultCredentialsProvider( provider ).build();


        HttpHead httphead = new HttpHead( artifactUrl );
        try 
        {
          HttpResponse response = httpclient.execute( httphead );
          if ( response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK )
          {
              alreadyInTarget = true;
          }
        } 
        catch ( ClientProtocolException cpe ) 
        {
          cpe.printStackTrace();
        } 
        catch ( IOException ioe ) 
        {
          ioe.printStackTrace();
        } 
        finally 
        {
           httpclient.getConnectionManager().shutdown();
        }
        return alreadyInTarget;
    }


    public String listSucessfulDeployments()
    {
        if ( successfulDeploys.isEmpty() )
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append( "Sucessful Deployments:\n\n" );
        for ( String artifact : successfulDeploys )
        {
            builder.append( artifact + "\n" );
        }
        return builder.toString();
    }

    public String listFailedDeployments()
    {
        if ( failedDeploys.isEmpty() )
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append( "Failed Deployments:\n\n" );
        for ( String artifact : failedDeploys )
        {
            builder.append( artifact + "\n" );
        }

        return builder.toString();
    }
    
    public String listSkippedDeployment()
    {
        if ( skippedDeploys.isEmpty() )
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append( "Skipped Deployments (POM already in target):\n\n" );
        for ( String artifact : skippedDeploys )
        {
            builder.append( artifact + "\n" );
        }

        return builder.toString();
    }

    public String listPotentialDeployment()
    {
        if ( potentialDeploys.isEmpty() )
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append( "Potential Deployments :\n\n" );
        for ( String artifact : potentialDeploys )
        {
            builder.append( artifact + "\n" );
        }

        return builder.toString();
    }

    public static Gav getCoordinates ( File pomFile ) throws Exception
    {
        BufferedReader in = new BufferedReader( new FileReader( pomFile ) );
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read( in );
        // get coordinates and take care of inheritance and default
        String g = model.getGroupId();
        if ( StringUtils.isEmpty( g ) ) 
        {
            g = model.getParent().getGroupId();
        }
        String a = model.getArtifactId();
        if ( StringUtils.isEmpty( a ) ) 
        {
            a = model.getParent().getArtifactId();
        }
        String v = model.getVersion();
        if ( StringUtils.isEmpty( v ) ) 
        {
            v = model.getParent().getVersion();
        }
        String p = model.getPackaging();
        if ( StringUtils.isEmpty( p ) ) 
        {
            p = MavenConstants.JAR;
        }
        Gav gav = new Gav( g, a, v, p );
        return gav;
    }

    public boolean hasFailure() 
    {
      return failedDeploys.size() > 0;
    }

    public String getFailureMessage() 
    {
      return "Failed to deploy some artifacts.";
    }
}
