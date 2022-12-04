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

package org.eclipse.jetty.fcgi.server;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.LeakTrackingConnectionPool;
import org.eclipse.jetty.fcgi.client.http.HttpClientTransportOverFCGI;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.LeakTrackingByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.LeakDetector;
import org.eclipse.jetty.util.ProcessorUtils;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;

import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractHttpClientServerTest
{
    private LeakTrackingByteBufferPool serverBufferPool;
    protected ByteBufferPool clientBufferPool;
    private final AtomicLong connectionLeaks = new AtomicLong();
    protected Server server;
    protected ServerConnector connector;
    protected HttpClient client;
    protected String scheme = HttpScheme.HTTP.asString();

    public void start(Handler handler) throws Exception
    {
        server = new Server();

        ServerFCGIConnectionFactory fcgiConnectionFactory = new ServerFCGIConnectionFactory(new HttpConfiguration());
        serverBufferPool = new LeakTrackingByteBufferPool(new MappedByteBufferPool.Tagged());
        connector = new ServerConnector(server, null, null, serverBufferPool,
            1, Math.max(1, ProcessorUtils.availableProcessors() / 2), fcgiConnectionFactory);
//        connector.setPort(9000);

        server.addConnector(connector);
        server.setHandler(handler);
        server.start();

        QueuedThreadPool executor = new QueuedThreadPool();
        executor.setName(executor.getName() + "-client");

        HttpClientTransport transport = new HttpClientTransportOverFCGI(1, "");
        transport.setConnectionPoolFactory(destination -> new LeakTrackingConnectionPool(destination, client.getMaxConnectionsPerDestination(), destination)
        {
            @Override
            protected void leaked(LeakDetector.LeakInfo leakInfo)
            {
                connectionLeaks.incrementAndGet();
            }
        });
        client = new HttpClient(transport);
        client.setExecutor(executor);
        if (clientBufferPool == null)
            clientBufferPool = new LeakTrackingByteBufferPool(new MappedByteBufferPool.Tagged());
        client.setByteBufferPool(clientBufferPool);
        client.start();
    }

    @AfterEach
    public void dispose() throws Exception
    {
        System.gc();

        if (serverBufferPool != null)
        {
            assertThat("Server BufferPool - leaked acquires", serverBufferPool.getLeakedAcquires(), Matchers.is(0L));
            assertThat("Server BufferPool - leaked releases", serverBufferPool.getLeakedReleases(), Matchers.is(0L));
            assertThat("Server BufferPool - leaked removes", serverBufferPool.getLeakedRemoves(), Matchers.is(0L));
            assertThat("Server BufferPool - unreleased", serverBufferPool.getLeakedResources(), Matchers.is(0L));
        }

        if ((clientBufferPool != null) && (clientBufferPool instanceof LeakTrackingByteBufferPool))
        {
            LeakTrackingByteBufferPool pool = (LeakTrackingByteBufferPool)clientBufferPool;
            assertThat("Client BufferPool - leaked acquires", pool.getLeakedAcquires(), Matchers.is(0L));
            assertThat("Client BufferPool - leaked releases", pool.getLeakedReleases(), Matchers.is(0L));
            assertThat("Client BufferPool - leaked removes", pool.getLeakedRemoves(), Matchers.is(0L));
            assertThat("Client BufferPool - unreleased", pool.getLeakedResources(), Matchers.is(0L));
        }

        assertThat("Connection Leaks", connectionLeaks.get(), Matchers.is(0L));

        if (client != null)
            client.stop();
        if (server != null)
            server.stop();
    }
}
