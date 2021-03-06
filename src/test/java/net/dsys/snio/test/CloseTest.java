/**
 * Copyright 2014 Ricardo Padilha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dsys.snio.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import net.dsys.commons.impl.future.SettableFuture;
import net.dsys.snio.api.buffer.MessageBufferConsumer;
import net.dsys.snio.api.buffer.MessageBufferProducer;
import net.dsys.snio.api.channel.MessageChannel;
import net.dsys.snio.api.channel.MessageServerChannel;
import net.dsys.snio.api.pool.SelectorPool;
import net.dsys.snio.demo.DemoSSLContext;
import net.dsys.snio.impl.channel.MessageChannels;
import net.dsys.snio.impl.channel.MessageServerChannels;
import net.dsys.snio.impl.channel.builder.ChannelConfig;
import net.dsys.snio.impl.channel.builder.ClientConfig;
import net.dsys.snio.impl.channel.builder.SSLConfig;
import net.dsys.snio.impl.channel.builder.ServerConfig;
import net.dsys.snio.impl.pool.SelectorPools;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class CloseTest {

	private static final int SEC = 1_000_000_000;
	private static final int CAPACITY = 1;
	private static final int LENGTH = 8;
	private static final int PORT = 64535;

	private AtomicInteger atomicPort = new AtomicInteger(PORT);
	private SelectorPool pool;
	private ChannelConfig<ByteBuffer> common;
	private ClientConfig client;
	private ServerConfig server;
	private SSLConfig ssl;

	public CloseTest() {
		super();
	}

	@Before
	public void setUp() throws Exception {
		pool = SelectorPools.open("test", 1);
		common = new ChannelConfig<ByteBuffer>()
				.setPool(pool)
				.setBufferCapacity(CAPACITY);
		client = new ClientConfig().setMessageLength(LENGTH);
		server = new ServerConfig().setMessageLength(LENGTH);
		ssl = new SSLConfig().setContext(DemoSSLContext.getDemoContext());
	}

	@After
	public void tearDown() throws Exception {
		assert pool != null;
		if (pool.isOpen()) {
			pool.close();
			pool.getCloseFuture().get();
		}
	}

	@Test
	public void testClosePool() throws InterruptedException, ExecutionException, IOException {
		assert pool != null;
		assertTrue(pool.isOpen());
		pool.close();
		pool.getCloseFuture().get();
		assertFalse(pool.isOpen());
	}

	private static void testClosePoolThenChannel(final SelectorPool pool, final MessageChannel<?> channel)
			throws Exception {
		assertTrue(pool.isOpen());
		assertTrue(channel.isOpen());
		pool.close();
		pool.getCloseFuture().get();
		assertFalse(pool.isOpen());
		assertTrue(channel.isOpen());
		channel.close();
		channel.getCloseFuture().get();
		assertFalse(pool.isOpen());
		assertFalse(channel.isOpen());
	}

	private static void testCloseChannelThenPool(final SelectorPool pool, final MessageChannel<?> channel)
			throws Exception {
		assertTrue(pool.isOpen());
		assertTrue(channel.isOpen());
		channel.close();
		channel.getCloseFuture().get();
		assertTrue(pool.isOpen());
		assertFalse(channel.isOpen());
		pool.close();
		pool.getCloseFuture().get();
		assertFalse(pool.isOpen());
		assertFalse(channel.isOpen());
	}

	@Test
	public void testClosePoolThenChannelTCP() throws Exception {
		final MessageChannel<?> channel = MessageChannels.openTCPChannel(common, client);
		testClosePoolThenChannel(pool, channel);
	}

	@Test
	public void testCloseChannelThenPoolTCP() throws Exception {
		final MessageChannel<?> channel = MessageChannels.openTCPChannel(common, client);
		testCloseChannelThenPool(pool, channel);
	}

	@Test
	public void testClosePoolThenChannelSSL() throws Exception {
		final MessageChannel<?> channel = MessageChannels.openSSLChannel(common, client, ssl);
		testClosePoolThenChannel(pool, channel);
	}

	@Test
	public void testCloseChannelThenPoolSSL() throws Exception {
		final MessageChannel<?> channel = MessageChannels.openSSLChannel(common, client, ssl);
		testCloseChannelThenPool(pool, channel);
	}

	private static void testClosePoolThenChannel(final SelectorPool pool, final MessageServerChannel<?> channel)
			throws Exception {
		assertTrue(pool.isOpen());
		assertTrue(channel.isOpen());
		pool.close();
		pool.getCloseFuture().get();
		assertFalse(pool.isOpen());
		assertTrue(channel.isOpen());
		channel.close();
		channel.getCloseFuture().get();
		assertFalse(pool.isOpen());
		assertFalse(channel.isOpen());
	}

	private static void testCloseChannelThenPool(final SelectorPool pool, final MessageServerChannel<?> channel)
			throws Exception {
		assertTrue(pool.isOpen());
		assertTrue(channel.isOpen());
		channel.close();
		channel.getCloseFuture().get();
		assertTrue(pool.isOpen());
		assertFalse(channel.isOpen());
		pool.close();
		pool.getCloseFuture().get();
		assertFalse(pool.isOpen());
		assertFalse(channel.isOpen());
	}

	@Test
	public void testClosePoolThenServerChannelTCP() throws Exception {
		final MessageServerChannel<?> channel = MessageServerChannels.openTCPServerChannel(common, server);
		testClosePoolThenChannel(pool, channel);
	}

	@Test
	public void testCloseServerChannelThenPoolTCP() throws Exception {
		final MessageServerChannel<?> channel = MessageServerChannels.openTCPServerChannel(common, server);
		testCloseChannelThenPool(pool, channel);
	}

	@Test
	public void testBindTCP() throws Exception {
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);

		final MessageServerChannel<?> channel = MessageServerChannels.openTCPServerChannel(common, server);
		assertTrue(channel.isOpen());
		try {
			channel.bind(local);
			channel.getBindFuture().get();
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			return;
		} finally {
			assertTrue(channel.isOpen());
			channel.close();
			channel.getCloseFuture().get();
			assertFalse(channel.isOpen());
		}
	}

	@Test
	public void testBindSSL() throws Exception {
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);

		final MessageServerChannel<?> channel = MessageServerChannels.openSSLServerChannel(common, server, ssl);
		assertTrue(channel.isOpen());
		try {
			channel.bind(local);
			channel.getBindFuture().get();
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			return;
		} finally {
			assertTrue(channel.isOpen());
			channel.close();
			channel.getCloseFuture().get();
			assertFalse(channel.isOpen());
		}
	}

	@Test
	public void testFailedBindTCP() throws Exception {
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);

		final ServerSocketChannel socket = ServerSocketChannel.open();
		try {
			socket.bind(local);
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			socket.close();
			return;
		}

		final MessageChannel<?> channel = MessageChannels.openTCPChannel(common, client);
		try {
			channel.bind(local);
			channel.getBindFuture().get();
		} catch (final BindException e) {
			assertNotNull(e);
			return;
		} finally {
			socket.close();
			channel.close();
			channel.getCloseFuture().get();
		}
		fail("should have got a BindException");
	}

	@Test
	public void testFailedBindSSL() throws Exception {
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);

		final ServerSocketChannel socket = ServerSocketChannel.open();
		try {
			socket.bind(local);
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			socket.close();
			return;
		}

		final MessageChannel<?> channel = MessageChannels.openSSLChannel(common, client, ssl);
		try {
			channel.bind(local);
			channel.getBindFuture().get();
		} catch (final BindException e) {
			assertNotNull(e);
			return;
		} finally {
			socket.close();
			channel.close();
			channel.getCloseFuture().get();
		}
		fail("should have got a BindException");
	}

	@Test
	public void testConnectionTCP() throws Exception {
		final InetAddress addr = InetAddress.getLocalHost();
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);
		final InetSocketAddress remote = new InetSocketAddress(addr, port);

		final ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(true);
		try {
			server.bind(local);
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			server.close();
			return;
		}

		final MessageChannel<?> channel = MessageChannels.openTCPChannel(common, client);
		assertTrue(channel.isOpen());
		channel.connect(remote);

		final SocketChannel endpoint = server.accept();
		assertNotNull(endpoint);

		channel.getConnectFuture().get();

		channel.close();
		channel.getCloseFuture().get();
		assertFalse(channel.isOpen());

		endpoint.close();
		server.close();
	}

	@Test
	public void testConnectionSSL() throws Exception {
		final InetAddress addr = InetAddress.getLocalHost();
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);
		final InetSocketAddress remote = new InetSocketAddress(addr, port);

		final MessageServerChannel<?> server = MessageServerChannels.openSSLServerChannel(common, this.server, ssl);
		try {
			server.bind(local);
			server.getBindFuture().get();
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			server.close();
			return;
		}

		final MessageChannel<?> client = MessageChannels.openSSLChannel(common, this.client, ssl);
		assertTrue(client.isOpen());
		client.connect(remote);
		client.getConnectFuture().get();

		//LockSupport.parkNanos(SEC);

		client.close();
		client.getCloseFuture().get();
		assertFalse(client.isOpen());

		server.close();
		server.getCloseFuture().get();
	}

	@Test
	public void testFailedConnectionTCP() throws Exception {
		final InetAddress addr = InetAddress.getLocalHost();
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress remote = new InetSocketAddress(addr, port);

		final MessageChannel<?> channel = MessageChannels.openTCPChannel(common, client);
		assertTrue(channel.isOpen());
		channel.connect(remote);
		try {
			channel.getConnectFuture().get();
		} catch (final ExecutionException e) {
			assertTrue(e.getCause() instanceof ConnectException);
			return;
		} finally {
			channel.close();
			channel.getCloseFuture().get();
			assertFalse(channel.isOpen());
		}
		fail("should have got a ConnectException");
	}

	@Test
	public void testServerClosedConnectionTCP() throws Exception {
		final InetAddress addr = InetAddress.getLocalHost();
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);
		final InetSocketAddress remote = new InetSocketAddress(addr, port);

		final ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(true);
		try {
			server.bind(local);
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			server.close();
			return;
		}

		final MessageChannel<?> channel = MessageChannels.openTCPChannel(common, client);
		assertTrue(channel.isOpen());
		channel.connect(remote);

		final SocketChannel endpoint = server.accept();
		assertNotNull(endpoint);

		channel.getConnectFuture().get();

		endpoint.close();
		server.close();

		channel.close();
		channel.getCloseFuture().get();
		assertFalse(channel.isOpen());

	}

	@Test
	public void testClientInterruptWriteTCP() throws Exception {
		final InetAddress addr = InetAddress.getLocalHost();
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);
		final InetSocketAddress remote = new InetSocketAddress(addr, port);

		final ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(true);
		try {
			server.bind(local);
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			server.close();
			return;
		}

		final MessageChannel<ByteBuffer> channel = MessageChannels.openTCPChannel(common, client);
		assertTrue(channel.isOpen());
		channel.connect(remote);

		final SocketChannel endpoint = server.accept();
		assertNotNull(endpoint);

		channel.getConnectFuture().get();

		final CountDownLatch latch = new CountDownLatch(1);
		final SettableFuture<Void> future = new SettableFuture<>();
		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final MessageBufferProducer<ByteBuffer> out = channel.getOutputBuffer();
					latch.await();
					out.acquire();
					future.fail(new AssertionError("able to acquire despite remote endpoint being closed"));
				} catch (final InterruptedException e) {
					future.success(null);
				}
			}
		});
		t.start();

		endpoint.close();
		server.close();
		// wait for a while so that the close propagates through the TCP stack
		LockSupport.parkNanos(SEC);

		// tells the other thread to write, and wait for outcome
		latch.countDown();
		try {
			future.get();
		} finally {
			channel.close();
			channel.getCloseFuture().get();
			assertFalse(channel.isOpen());
		}
	}

	@Test
	public void testClientInterruptReadTCP() throws Exception {
		final InetAddress addr = InetAddress.getLocalHost();
		final int port = atomicPort.getAndDecrement();
		final InetSocketAddress local = new InetSocketAddress(port);
		final InetSocketAddress remote = new InetSocketAddress(addr, port);

		final ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(true);
		try {
			server.bind(local);
		} catch (final BindException e) {
			fail("test failed: test port is already occupied -- make sure that no other process is using that port");
			server.close();
			return;
		}

		final MessageChannel<ByteBuffer> channel = MessageChannels.openTCPChannel(common, client);
		assertTrue(channel.isOpen());
		channel.connect(remote);

		final SocketChannel endpoint = server.accept();
		assertNotNull(endpoint);

		channel.getConnectFuture().get();

		final SettableFuture<Void> future = new SettableFuture<>();
		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final MessageBufferConsumer<ByteBuffer> in = channel.getInputBuffer();
					in.acquire();
					future.fail(new AssertionError("able to acquire despite remote endpoint being closed"));
				} catch (final InterruptedException e) {
					future.success(null);
				}
			}
		});
		t.start();

		endpoint.close();
		server.close();
		// wait for a while so that the close propagates through the TCP stack
		LockSupport.parkNanos(SEC);

		// tells the other thread to write, and wait for outcome
		future.get();

		channel.close();
		channel.getCloseFuture().get();
		assertFalse(channel.isOpen());
	}
}
