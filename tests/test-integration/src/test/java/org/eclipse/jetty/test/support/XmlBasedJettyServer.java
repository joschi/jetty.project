//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.test.support;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allows for setting up a Jetty server for testing based on XML configuration files.
 */
public class XmlBasedJettyServer
{
    private static final Logger LOG = LoggerFactory.getLogger(XmlBasedJettyServer.class);
    private List<Resource> _xmlConfigurations;
    private final Map<String, String> _properties = new HashMap<>();
    private Server _server;
    private int _serverPort;
    private String _scheme = HttpScheme.HTTP.asString();

    /* Popular Directories */
    private Path baseDir;
    private Path testResourcesDir;

    public XmlBasedJettyServer() throws IOException
    {
        _xmlConfigurations = new ArrayList<>();
        Properties properties = new Properties();

        /* Establish Popular Directories */
        baseDir = MavenTestingUtils.getBasePath();
        properties.setProperty("test.basedir", baseDir.toString());

        testResourcesDir = MavenTestingUtils.getTestResourcesPath();
        properties.setProperty("test.resourcesdir", testResourcesDir.toString());

        Path testDocRoot = MavenTestingUtils.getTestResourcePathDir("docroots");
        properties.setProperty("test.docroot.base", testDocRoot.toString());

        Path targetDir = MavenTestingUtils.getTargetPath();
        properties.setProperty("test.targetdir", targetDir.toString());

        Path webappsDir = MavenTestingUtils.getTargetPath("webapps");
        properties.setProperty("test.webapps", webappsDir.toString());

        Path keystorePath = MavenTestingUtils.getTestResourcePathFile("keystore.p12");
        properties.setProperty("jetty.sslContext.keyStorePath", keystorePath.toString());
        properties.setProperty("jetty.sslContext.keyStorePassword", "storepwd");

        // Write out configuration for use by ConfigurationManager.
        Path testConfig = targetDir.resolve("testable-jetty-server-config.properties");
        try (OutputStream out = Files.newOutputStream(testConfig))
        {
            properties.store(out, "Generated by " + XmlBasedJettyServer.class.getName());
        }

        for (Object key : properties.keySet())
        {
            _properties.put(String.valueOf(key), String.valueOf(properties.get(key)));
        }
    }

    public void addXmlConfiguration(Resource xmlConfig)
    {
        _xmlConfigurations.add(xmlConfig);
    }

    public void addXmlConfiguration(File xmlConfigFile)
    {
        _xmlConfigurations.add(new PathResource(xmlConfigFile));
    }

    public void addXmlConfiguration(String testConfigName)
    {
        addXmlConfiguration(MavenTestingUtils.getTestResourceFile(testConfigName));
    }

    public void setProperty(String key, String value)
    {
        _properties.put(key, value);
    }

    public void load() throws Exception
    {
        XmlConfiguration last = null;
        Object[] obj = new Object[this._xmlConfigurations.size()];

        // Configure everything
        for (int i = 0; i < this._xmlConfigurations.size(); i++)
        {
            Resource configResource = this._xmlConfigurations.get(i);
            LOG.debug("configuring: " + configResource);
            XmlConfiguration configuration = new XmlConfiguration(configResource);
            if (last != null)
            {
                configuration.getIdMap().putAll(last.getIdMap());
            }
            configuration.getProperties().putAll(_properties);
            obj[i] = configuration.configure();
            last = configuration;
        }

        // Test for Server Instance.
        Server foundServer = null;
        int serverCount = 0;
        for (int i = 0; i < this._xmlConfigurations.size(); i++)
        {
            if (obj[i] instanceof Server)
            {
                if (obj[i].equals(foundServer))
                {
                    // Identical server instance found
                    break;
                }
                foundServer = (Server)obj[i];
                serverCount++;
            }
        }

        if (serverCount <= 0)
        {
            throw new Exception("Load failed to configure a " + Server.class.getName());
        }

        assertEquals(1, serverCount, "Server load count");

        this._server = foundServer;
        this._server.setStopTimeout(2000);
    }

    public String getScheme()
    {
        return _scheme;
    }

    public void setScheme(String scheme)
    {
        this._scheme = scheme;
    }

    public void start() throws Exception
    {
        assertNotNull(_server, "Server should not be null (failed load?)");

        _server.start();

        // Find the active server port.
        this._serverPort = ((NetworkConnector)_server.getConnectors()[0]).getLocalPort();
        assertTrue((1 <= this._serverPort) && (this._serverPort <= 65535), "Server Port is between 1 and 65535. Actually <" + _serverPort + ">");
    }

    public int getServerPort()
    {
        return _serverPort;
    }

    public void stop() throws Exception
    {
        _server.stop();
    }

    public URI getServerURI()
    {
        StringBuffer uri = new StringBuffer();
        uri.append(this._scheme).append("://");
        uri.append(InetAddress.getLoopbackAddress().getHostAddress());
        uri.append(":").append(this._serverPort);
        return URI.create(uri.toString());
    }

    public Server getServer()
    {
        return _server;
    }
}
