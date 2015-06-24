package u14.udpx.tick;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zhangheng
 */
public class TickHelper {

	private volatile static ScheduledThreadPoolExecutor executor = reset(Runtime.getRuntime().availableProcessors());
	
    /**
     * 每次执行任务的时间不受前次任务延时影响。
     * @param task			具体待执行的任务
     * @param delay		           等待多久执行任务
     * @param unit 			时间单位
     */
    public static Future<?> timeout(Runnable task, long delay, TimeUnit unit) {
    	return executor.schedule(task, delay, unit);
    }
    /**
     * 每次执行任务的时间不受前次任务延时影响。
     * @param task			具体待执行的任务
     * @param interval		每次执行任务的间隔时间
     * @param unit 			时间单位
     */
    public static Future<?> interval(Runnable task, long interval, TimeUnit unit) {
        return interval(task, interval, interval, unit);
    }
    /**
     * 在指定的延时之后开始以固定的频率来运行任务。后续任务的启动时间不受前次任务延时影响。
     * @param task			具体待执行的任务
     * @param firstDelay	首次执行任务的延时时间
     * @param interval		每次执行任务的间隔时间
     * @param unit 			时间单位
     */
    public static Future<?> interval(Runnable task, long firstDelay, long interval, TimeUnit unit) {
    	return executor.scheduleAtFixedRate(task, firstDelay, interval, unit);
    }
    /**
     * 在指定的延时之后开始以固定的频率来运行任务。后续任务的启动时间不受前次任务延时影响。
     * @param task          具体待执行的任务
     * @param interval      每次执行任务的间隔时间
     * @param unit          时间单位
     */
    public static Future<?> intervalWithFixedDelay(Runnable task, long interval, TimeUnit unit) {
        return executor.scheduleWithFixedDelay(task, interval, interval, unit);
    }
    /**
     * 每次执行任务的时间不受前次任务延时影响。
     * @param task			具体待执行的任务
     * @param interval		等待多久执行任务
     * @param unit 			时间单位
     */
    public static <T> Future<T> timeout(Callable<T> task, long delay, TimeUnit unit) {
        return executor.schedule(task, delay, unit);
    }
    
    public static Future<?> submit(Runnable task){
    	return executor.submit(task);
    }
    public static <T> Future<T> submit(Callable<T> task){
    	return executor.submit(task);
    }
    
    /**
     * 重置动定时任务服务，旧的任务如果没执行可能会被shutdown
     */
	public synchronized static ScheduledThreadPoolExecutor reset(int size) {
    	try{
    		if(executor!=null){
    			executor.shutdown();
    		}
    	}finally{
    		executor = new ScheduledThreadPoolExecutor(Math.max(1, size), new TickThreadFactory("TickHelper"));
    	}
    	return executor;
    }
}
