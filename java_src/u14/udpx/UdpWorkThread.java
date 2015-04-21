package u14.udpx;

import java.net.DatagramPacket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 数据处理代理线程<br/>
 * 用于帮助udp_socket接受数据后快速返回recive
 * @author zhangheng
 */
public class UdpWorkThread {

	private LinkedBlockingQueue<DatagramPacket> queue;
	private Thread thread;
	private UdpListener listener;
	private volatile boolean alive;
	private UdpSocket owner;
	
	public void listen(UdpListener l){
		this.listener = l;
	}
	
	public UdpWorkThread(UdpSocket owner) {
		this(owner, 0);
	}
	public UdpWorkThread(UdpSocket owner, int capacity) {
		this.queue = new LinkedBlockingQueue<DatagramPacket>(capacity<=0?Short.MAX_VALUE:capacity);
		this.owner = owner;
	}

	public boolean offer(DatagramPacket d){
		return this.queue.offer(d);
	}
	
	public void start(){
		if(thread==null){
			thread = new Thread(UdpWorkThread.class.getSimpleName()){
				public void run(){
					while(UdpWorkThread.this.isAlive()){
						try {
							UdpListener l = listener;
							if(l!=null){
								DatagramPacket packet = queue.take();
								if(packet!=null){
									l.handData(UdpWorkThread.this.owner, packet);
								}
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			};
			alive = true;
			thread.start();
		}
	}
	public void stop(){
		if(alive){
			alive = false;
			if(thread!=null){
				thread.interrupt();
				
				queue.add(null);
				DatagramPacket packet;
				while(listener!=null && (packet = queue.poll())!=null){
					UdpListener l = listener;
					if(l!=null){
						l.handData(owner, packet);
					}else{
						break;
					}
				}
				queue.clear();
				thread = null;
			}
		}
	}
	protected boolean isAlive() {
		return alive;
	}
	
}
