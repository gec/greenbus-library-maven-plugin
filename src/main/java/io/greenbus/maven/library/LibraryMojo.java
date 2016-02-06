/**
 * Copyright 2011-2016 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.greenbus.maven.library;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.*;
import java.util.List;


/**
 * Analyzes list of jars for building the classpath.
 *
 * @goal jarlist
 * @requiresDependencyResolution
 */
public class LibraryMojo extends AbstractMojo {

    /**
     * Configuration.
     *
     * @parameter
     * @readonly
     * @required
     */
    private TargetConfig[] targets;

    /**
     * The current Maven project.
     *
     * @parameter default-value="${project}"
     * @readonly
     * @required
     */
    private MavenProject project;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> projectRepos;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     *
     * @parameter default-value="${project.remotePluginRepositories}"
     * @readonly
     */
    private List<RemoteRepository> pluginRepos;

    /**
     *
     * @parameter default-value="${project.build.outputDirectory}"
     * @readonly
     */
    private String outputDirectory;


    public void execute() throws MojoExecutionException
    {

        for (TargetConfig target : targets) {
            buildOutput(target.getRootArtifact(), target.getOutputFile());
        }

    }


    private void buildOutput(String rootArtifact, String outputFile) throws MojoExecutionException {

        //
        // Based on code from maven dependency plugin
        //

        Dependency dependency = new Dependency( new DefaultArtifact( rootArtifact ), "run" );

        CollectRequest collectRequest = new CollectRequest(dependency, projectRepos);

        try {
            DependencyNode node = repoSystem.collectDependencies( repoSession, collectRequest ).getRoot();
            DependencyRequest dependencyRequest = new DependencyRequest( node, null );

            repoSystem.resolveDependencies( repoSession, dependencyRequest  );

            PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
            node.accept( nlg );

            StringBuilder sb = new StringBuilder();


            for (File file : nlg.getFiles()) {
                FileUtils.copyFileToDirectory(file, new File(outputDirectory + "/lib"));
                sb.append(file.getName() + "\n");
            }

            writeListFile(sb.toString(), outputFile);

        } catch (DependencyCollectionException e) {
            throw new MojoExecutionException("Failed to resolve dependencies.", e);
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to resolve dependencies.", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy dependencies.", e);
        }
    }

    private void writeListFile(String cpString, String out) throws MojoExecutionException {

        String fullPath = outputDirectory + "/" + out;
        File outFile = new File(fullPath);
        File directories = outFile.getParentFile();
        if( directories != null ) {
            directories.mkdirs();
        }
        outFile.getParentFile().mkdirs();

        Writer w = null;
        try
        {
            w = new BufferedWriter( new FileWriter( outFile ) );
            w.write( cpString );
            getLog().info( "Wrote jar list '" + out + "'." );
        }
        catch ( IOException ex )
        {
            throw new MojoExecutionException( "Error while writing jar list file '" + out + "': " + ex.toString(), ex );
        }
        finally
        {
            IOUtil.close(w);
        }
    }
}
