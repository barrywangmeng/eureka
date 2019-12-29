package com.netflix.eureka.util.batcher;

/**
 * See {@link TaskDispatcher} for an overview.
 *
 * @author Tomasz Bak
 */
public class TaskDispatchers {

    public static <ID, T> TaskDispatcher<ID, T> createNonBatchingTaskDispatcher(String id,
                                                                                int maxBufferSize,
                                                                                int workerCount,
                                                                                long maxBatchingDelay,
                                                                                long congestionRetryDelayMs,
                                                                                long networkFailureRetryMs,
                                                                                TaskProcessor<T> taskProcessor) {
        final AcceptorExecutor<ID, T> acceptorExecutor = new AcceptorExecutor<>(
                id, maxBufferSize, 1, maxBatchingDelay, congestionRetryDelayMs, networkFailureRetryMs
        );
        final TaskExecutors<ID, T> taskExecutor = TaskExecutors.singleItemExecutors(id, workerCount, taskProcessor, acceptorExecutor);
        return new TaskDispatcher<ID, T>() {
            @Override
            public void process(ID id, T task, long expiryTime) {
                acceptorExecutor.process(id, task, expiryTime);
            }

            @Override
            public void shutdown() {
                acceptorExecutor.shutdown();
                taskExecutor.shutdown();
            }
        };
    }

    public static <ID, T> TaskDispatcher<ID, T> createBatchingTaskDispatcher(String id,
                                                                             int maxBufferSize,
                                                                             int workloadSize,
                                                                             int workerCount,
                                                                             long maxBatchingDelay,
                                                                             long congestionRetryDelayMs,
                                                                             long networkFailureRetryMs,
                                                                             TaskProcessor<T> taskProcessor) {
    	// 初始化AcceptorExecutor() 会启动一个后台线程：AcceptorRunner，这里面处理batchWorkQueue
        final AcceptorExecutor<ID, T> acceptorExecutor = new AcceptorExecutor<>(
                id, maxBufferSize, workloadSize, maxBatchingDelay, congestionRetryDelayMs, networkFailureRetryMs
        );
        // taskProcessor为ReplicationTaskProcessor，这里会执行ReplicationTaskProcessor中的#process()
		// 最后是发送一个replicationClient.submitBatchUpdates请求
        final TaskExecutors<ID, T> taskExecutor = TaskExecutors.batchExecutors(id, workerCount, taskProcessor, acceptorExecutor);
        return new TaskDispatcher<ID, T>() {
            @Override
            public void process(ID id, T task, long expiryTime) {
            	// 构建第一层队列：acceptorQueue
                acceptorExecutor.process(id, task, expiryTime);
            }

            @Override
            public void shutdown() {
                acceptorExecutor.shutdown();
                taskExecutor.shutdown();
            }
        };
    }
}
