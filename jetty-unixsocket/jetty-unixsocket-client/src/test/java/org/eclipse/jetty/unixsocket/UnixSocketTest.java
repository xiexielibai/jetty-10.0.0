//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.unixsocket;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.unixsocket.client.HttpClientTransportOverUnixSockets;
import org.eclipse.jetty.unixsocket.server.UnixSocketConnector;
import org.eclipse.jetty.util.StringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

@EnabledOnOs({LINUX, MAC})
public class UnixSocketTest
{
    private static final Logger log = LoggerFactory.getLogger(UnixSocketTest.class);

    private Server server;
    private HttpClient httpClient;
    private Path sockFile;

    @BeforeEach
    public void before() throws Exception
    {
        server = null;
        httpClient = null;
        String unixSocketTmp = System.getProperty("unix.socket.tmp");
        if (StringUtil.isNotBlank(unixSocketTmp))
            sockFile = Files.createTempFile(Paths.get(unixSocketTmp), "unix", ".sock");
        else
            sockFile = Files.createTempFile("unix", ".sock");
        if (sockFile.toAbsolutePath().toString().length() > UnixSocketConnector.MAX_UNIX_SOCKET_PATH_LENGTH)
        {
            Path tmp = Paths.get("/tmp");
            assumeTrue(Files.exists(tmp) && Files.isDirectory(tmp));
            sockFile = Files.createTempFile(tmp, "unix", ".sock");
        }
        assertTrue(Files.deleteIfExists(sockFile), "temp sock file cannot be deleted");
    }

    @AfterEach
    public void after() throws Exception
    {
        if (httpClient != null)
            httpClient.stop();
        if (server != null)
            server.stop();
        if (sockFile != null)
            assertFalse(Files.exists(sockFile));
    }

    @Test
    public void testUnixSocket() throws Exception
    {
        server = new Server();
        HttpConnectionFactory http = new HttpConnectionFactory();
        UnixSocketConnector connector = new UnixSocketConnector(server, http);
        connector.setUnixSocket(sockFile.toString());
        server.addConnector(connector);

        server.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                int l = 0;
                if (request.getContentLength() != 0)
                {
                    InputStream in = request.getInputStream();
                    byte[] buffer = new byte[4096];
                    int r = 0;
                    while (r >= 0)
                    {
                        l += r;
                        r = in.read(buffer);
                    }
                }
                log.info("UnixSocketTest: request received");
                baseRequest.setHandled(true);
                response.setStatus(200);
                response.getWriter().write("Hello World " + new Date() + "\r\n");
                response.getWriter().write(
                    "remote=" + request.getRemoteAddr() + ":" + request.getRemotePort() + "\r\n");
                response.getWriter().write(
                    "local =" + request.getLocalAddr() + ":" + request.getLocalPort() + "\r\n");
                response.getWriter().write("read =" + l + "\r\n");
            }
        });

        server.start();

        httpClient = new HttpClient(new HttpClientTransportOverUnixSockets(sockFile.toString()));
        httpClient.start();

        ContentResponse contentResponse = httpClient
            .newRequest("http://localhost")
            .send();

        log.debug("response from server: {}", contentResponse.getContentAsString());

        assertThat(contentResponse.getContentAsString(), containsString("Hello World"));
    }

    @Tag("external")
    @Test
    public void testNotLocal() throws Exception
    {
        httpClient = new HttpClient(new HttpClientTransportOverUnixSockets(sockFile.toString()));
        httpClient.start();

        ExecutionException e = assertThrows(ExecutionException.class, () -> httpClient.newRequest("http://google.com").send());
        assertThat(e.getCause(), instanceOf(ConnectException.class));
    }
}
