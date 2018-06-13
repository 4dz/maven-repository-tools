/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package com.simpligility.maven.provisioner;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simplistic repository listener that logs events to the log.
 */
public class LoggingRepositoryListener
    extends AbstractRepositoryListener
{

    private static Logger logger = LoggerFactory.getLogger( "LoggingRepositoryListener" );

    public LoggingRepositoryListener()
    {
    }

    public void artifactDeployed( RepositoryEvent event )
    {
        logger.trace( "Deployed " + event.getArtifact() + " to " + event.getRepository() );
    }

    public void artifactDeploying( RepositoryEvent event )
    {
        logger.trace( "Deploying " + event.getArtifact() + " to " + event.getRepository() );
    }

    public void artifactDescriptorInvalid( RepositoryEvent event )
    {
        logger.trace( "Invalid artifact descriptor for " + event.getArtifact() + ": "
            + event.getException().getMessage() );
    }

    public void artifactDescriptorMissing( RepositoryEvent event )
    {
        logger.trace( "Missing artifact descriptor for " + event.getArtifact() );
    }

    public void artifactInstalled( RepositoryEvent event )
    {
        logger.trace( "Installed " + event.getArtifact() + " to " + event.getFile() );
    }

    public void artifactInstalling( RepositoryEvent event )
    {
        logger.trace( "Installing " + event.getArtifact() + " to " + event.getFile() );
    }

    public void artifactResolved( RepositoryEvent event )
    {
        logger.trace( "Resolved artifact " + event.getArtifact() + " from " + event.getRepository() );
    }

    public void artifactDownloading( RepositoryEvent event )
    {
        logger.trace( "Downloading artifact " + event.getArtifact() + " from " + event.getRepository() );
    }

    public void artifactDownloaded( RepositoryEvent event )
    {
        logger.trace( "Downloaded artifact " + event.getArtifact() + " from " + event.getRepository() );
    }

    public void artifactResolving( RepositoryEvent event )
    {
        logger.trace( "Resolving artifact " + event.getArtifact() );
    }

    public void metadataDeployed( RepositoryEvent event )
    {
        logger.trace( "Deployed " + event.getMetadata() + " to " + event.getRepository() );
    }

    public void metadataDeploying( RepositoryEvent event )
    {
        logger.trace( "Deploying " + event.getMetadata() + " to " + event.getRepository() );
    }

    public void metadataInstalled( RepositoryEvent event )
    {
        logger.trace( "Installed " + event.getMetadata() + " to " + event.getFile() );
    }

    public void metadataInstalling( RepositoryEvent event )
    {
        logger.trace( "Installing " + event.getMetadata() + " to " + event.getFile() );
    }

    public void metadataInvalid( RepositoryEvent event )
    {
        logger.trace( "Invalid metadata " + event.getMetadata() );
    }

    public void metadataResolved( RepositoryEvent event )
    {
        logger.trace( "Resolved metadata " + event.getMetadata() + " from " + event.getRepository() );
    }

    public void metadataResolving( RepositoryEvent event )
    {
        logger.trace( "Resolving metadata " + event.getMetadata() + " from " + event.getRepository() );
    }
    
    
}
