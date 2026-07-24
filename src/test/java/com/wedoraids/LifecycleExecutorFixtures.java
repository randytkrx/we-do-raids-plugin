/*
 * Copyright (c) 2026, s59
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wedoraids;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

final class LifecycleExecutorFixtures
{
	private LifecycleExecutorFixtures()
	{
	}

	static Response blockingResponse(okhttp3.Interceptor.Chain chain, String body,
		CountDownLatch readStarted, CountDownLatch releaseRead, CountDownLatch bodyClosed)
	{
		Buffer bodyBuffer = new Buffer().writeUtf8(body);
		BufferedSource source = Okio.buffer(new ForwardingSource(bodyBuffer)
		{
			private boolean firstRead = true;

			@Override
			public long read(Buffer sink, long byteCount) throws IOException
			{
				if (firstRead)
				{
					firstRead = false;
					readStarted.countDown();
					await(releaseRead);
				}
				return super.read(sink, byteCount);
			}
		});
		ResponseBody responseBody = new ResponseBody()
		{
			@Override
			public MediaType contentType()
			{
				return MediaType.get("application/json");
			}

			@Override
			public long contentLength()
			{
				return body.length();
			}

			@Override
			public BufferedSource source()
			{
				return source;
			}

			@Override
			public void close()
			{
				try
				{
					super.close();
				}
				finally
				{
					bodyClosed.countDown();
				}
			}
		};
		return new Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200)
			.message("OK").body(responseBody).build();
	}

	static void await(CountDownLatch latch)
	{
		try
		{
			if (!latch.await(5, TimeUnit.SECONDS))
			{
				throw new AssertionError("Timed out waiting for test barrier");
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while waiting for test barrier", e);
		}
	}

	static final class CapturingScheduledExecutor extends ScheduledThreadPoolExecutor
	{
		private final List<Runnable> tasks = new ArrayList<>();
		private final List<CapturedScheduledFuture> futures = new ArrayList<>();
		private final CountDownLatch blockedSchedules;
		private final CountDownLatch releaseSchedules;

		CapturingScheduledExecutor()
		{
			this(0);
		}

		CapturingScheduledExecutor(int schedulesToBlock)
		{
			super(1);
			blockedSchedules = new CountDownLatch(schedulesToBlock);
			releaseSchedules = new CountDownLatch(schedulesToBlock == 0 ? 0 : 1);
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
			TimeUnit unit)
		{
			CapturedScheduledFuture future = new CapturedScheduledFuture();
			synchronized (this)
			{
				tasks.add(command);
				futures.add(future);
			}
			blockedSchedules.countDown();
			await(releaseSchedules);
			return future;
		}

		synchronized int taskCount()
		{
			return tasks.size();
		}

		synchronized CapturedScheduledFuture future(int index)
		{
			return futures.get(index);
		}

		synchronized void runTask(int index)
		{
			tasks.get(index).run();
		}

		void awaitBlockedSchedules()
		{
			await(blockedSchedules);
		}

		void releaseSchedules()
		{
			releaseSchedules.countDown();
		}

		synchronized int uncancelledFutureCount()
		{
			int count = 0;
			for (CapturedScheduledFuture future : futures)
			{
				if (!future.isCancelled())
				{
					count++;
				}
			}
			return count;
		}

		synchronized void cancelAll()
		{
			for (CapturedScheduledFuture future : futures)
			{
				future.cancel(false);
			}
		}
	}

	static final class ImmediateScheduledExecutor extends ScheduledThreadPoolExecutor
	{
		ImmediateScheduledExecutor()
		{
			super(1);
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
			TimeUnit unit)
		{
			command.run();
			return new CapturedScheduledFuture();
		}
	}

	static final class CapturedScheduledFuture implements ScheduledFuture<Object>
	{
		private volatile boolean cancelled;

		@Override
		public long getDelay(TimeUnit unit)
		{
			return 0;
		}

		@Override
		public int compareTo(Delayed other)
		{
			return 0;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			cancelled = true;
			return true;
		}

		@Override
		public boolean isCancelled()
		{
			return cancelled;
		}

		@Override
		public boolean isDone()
		{
			return cancelled;
		}

		@Override
		public Object get() throws InterruptedException, ExecutionException
		{
			return null;
		}

		@Override
		public Object get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			return null;
		}
	}
}
