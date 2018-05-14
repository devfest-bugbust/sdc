package org.openecomp.sdc.be.components.distribution.engine;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.openecomp.sdc.be.config.DistributionEngineConfiguration.DistributionNotificationTopicConfig;

public class NotificationExecutorServiceTest {

	private NotificationExecutorService createTestSubject() {
		return new NotificationExecutorService();
	}

	@Test
	public void testCreateExcecutorService() throws Exception {
		NotificationExecutorService testSubject;
		DistributionNotificationTopicConfig distributionNotificationTopic = new DistributionNotificationTopicConfig();
		ExecutorService result;

		// default test
		testSubject = createTestSubject();
		result = testSubject.createExcecutorService(distributionNotificationTopic);
		distributionNotificationTopic.setMinThreadPoolSize(1);
		result = testSubject.createExcecutorService(distributionNotificationTopic);
		distributionNotificationTopic.setMaxThreadPoolSize(1);
		result = testSubject.createExcecutorService(distributionNotificationTopic);
	}

	@Test
	public void testShutdownAndAwaitTermination() throws Exception {
		NotificationExecutorService testSubject;
		NotificationExecutorServiceMock pool = new NotificationExecutorServiceMock();
		long maxTimeToWait = 435435;

		// default test
		testSubject = createTestSubject();
		testSubject.shutdownAndAwaitTermination(pool, maxTimeToWait);
		pool.awaitTermination = true;
		testSubject.shutdownAndAwaitTermination(pool, maxTimeToWait);
		pool.awaitTermination = true;
		pool.isShutdownException = true;
		testSubject.shutdownAndAwaitTermination(pool, maxTimeToWait);
	}
	
	private class NotificationExecutorServiceMock implements ExecutorService {
		
		private boolean awaitTermination = false;
		private boolean isShutdownException = false;
		
		@Override
		public void execute(Runnable command) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void shutdown() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public List<Runnable> shutdownNow() {
			// TODO Auto-generated method stub
			if (isShutdownException) {
				try {
					throw new InterruptedException();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return null;
		}

		@Override
		public boolean isShutdown() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isTerminated() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			// TODO Auto-generated method stub
			return awaitTermination;
		}

		@Override
		public <T> Future<T> submit(Callable<T> task) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> Future<T> submit(Runnable task, T result) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Future<?> submit(Runnable task) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
				throws InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
				throws InterruptedException, ExecutionException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
}