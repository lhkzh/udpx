package u14.udpx;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import u14.udpx.frames.ACKFrame;
import u14.udpx.frames.DATFrame;
import u14.udpx.frames.EAKFrame;
import u14.udpx.frames.FINFrame;
import u14.udpx.frames.Frame;
import u14.udpx.frames.LIVFrame;
import u14.udpx.frames.SYNFrame;
import u14.udpx.tick.TickHelper;

/**
 * 客户端
 * @author zhangheng
 */
public class UxSocket {

	private static final int MaxFrameSize = 255;
	private static final int MaxRewriteNum = 8;
	private static final int INTERVAL_HEART = 30;
	private static final int INTERVAL_ALIVE = 90;
	private static final int SendTimeOut = 2500;
	private static final int MaxInQueueSize = 32;
	private static final int MaxSynQueueSize = 32;
	private static final int MaxOutQueueSize = 1024;
	private static final int DefaultConnectTimeOut = 5000;
	
	private SocketAddress address;
	protected UdpSocket socket;
	
	protected volatile UxSocketStat stat = UxSocketStat.INIT;
	protected Seq seq;
	
	private ArrayList<Frame> inQueue;//收到的帧队列(因为乱序-用于快速确认)
	private LinkedBlockingDeque<Frame> synQueue;//待ack确认的队列
	private LinkedBlockingDeque<Frame> outQueue;//待写出队列，防止一次大量写入阻塞超时
	
	private ReentrantLock locker;
	private UxSocketListener delegate;
	
	//多久未收到数据后断开
	private int heart_remote=0;
	//多久未写数据后，发alive
	private int heart_local=0;
	private int retry_interval=0;
	
	private Thread shutDownHook;
	private Runnable tickTask;
	private Future<?> tickFuture;
	
	private String closeInfo;
	public String closeInfo(){
		return closeInfo;
	}
	private static final int TICK_INTERVAL = 250;
	public UxSocket(){
		locker = new ReentrantLock();
		socket = UdpSocket.client();
		seq = new Seq();
		shutDownHook = new Thread(){
			public void run(){
				UxSocket.this.close();
			}
		};
		tickTask = new Runnable() {
			@Override
			public void run() {
				UxSocket.this.doTick();
			}
		};
	}
	/**
	 * 目标地址
	 * @return
	 */
	public SocketAddress address(){
		return address;
	}
	/**
	 * 连接目标地址
	 * @param addr
	 * @return
	 * @throws IOException
	 */
	public UxSocket connect(SocketAddress addr) throws IOException{
		this.connect(addr, DefaultConnectTimeOut);
		return this;
	}
	/**
	 * 建立连接
	 * @param addr
	 * @param timeout
	 * @throws IOException
	 */
	public synchronized void connect(SocketAddress addr, int timeout) throws IOException{
		if(this.stat==UxSocketStat.CONNECT){
			return;
		}
		this.address = addr;
		this.stat = UxSocketStat.CONNECT;
		seq.initNum();
		int oldTimeout = socket.socket().getSoTimeout();
		socket.socket().setSoTimeout(1000);
		Frame connectFrame = new SYNFrame(seq.num());
		DatagramPacket packet = new DatagramPacket(new byte[MaxFrameSize], MaxFrameSize);
		long startTm = System.currentTimeMillis();
		do{
		    try{
    		    sendFrameImp(connectFrame);
    		    socket.socket().receive(packet);
    		    break;
		    }catch(SocketTimeoutException e){
		        if((System.currentTimeMillis()-startTm)>timeout){
		            this.stat = UxSocketStat.CLOSED;
		            throw new IOException("CONNECT-TIME-OUT");
		        }
		    }
		}while(true);
		Frame frame = Frame.parse(packet.getData(), packet.getOffset(), packet.getLength());
		if(frame!=null && frame instanceof ACKFrame && frame.ack()==seq.num() && this.stat==UxSocketStat.CONNECT){
			seq.setLastInNum(frame.seq());
			socket.socket().setSoTimeout(oldTimeout);
			socket.listen(new UdpListener() {
				@Override
				public void handData(UdpSocket socket, DatagramPacket packet) {
					UxSocket.this.handData(packet);
				}
			}).start();
			this.stat = UxSocketStat.WORK;
			Runtime.getRuntime().addShutdownHook(shutDownHook);
			this.onOpen();
		}else{
			this.stat = UxSocketStat.CLOSED;
			throw new IOException("CONNECT-TIME-OUT");
		}
	}
	/**
	 * 添加侦听器
	 * @param listener
	 * @return
	 */
	public UxSocket listen(UxSocketListener listener){
		this.delegate = listener;
		return this;
	}
	protected void onOpen(){
		inQueue = new ArrayList<Frame>(MaxInQueueSize);
		synQueue = new LinkedBlockingDeque<Frame>(MaxSynQueueSize);
		outQueue = new LinkedBlockingDeque<Frame>(MaxOutQueueSize);
		if(this.delegate!=null){
			this.delegate.onOpen(this);
		}
		if(tickFuture!=null){
			tickFuture.cancel(true);
		}
		tickFuture = TickHelper.timeout(tickTask, 250, TimeUnit.MILLISECONDS);
	}
	public boolean isConnected(){
		return this.stat==UxSocketStat.WORK;
	}
	protected void reciveData(DATFrame frame) {
		if(delegate!=null){
			delegate.onData(this, frame.getData());
		}
//		System.out.println("recv:"+frame.seq()+", "+seq.getLastInNum());
	}
	/**
	 * 关闭连接
	 */
	public synchronized void close(){
		if(this.stat==UxSocketStat.WORK){
			this.sendFrame(new FINFrame(seq.next()));
			this.stat = UxSocketStat.CLOSED;
			this.closeImp();
		}else{
			this.stat = UxSocketStat.CLOSED;
		}
	}
	protected synchronized void closeImp(){
		this.socket.stop();
		if(this.delegate!=null){
			this.delegate.onClose(this);
		}
		if(tickFuture!=null){
			tickFuture.cancel(true);
			tickFuture = null;
		}
		this.synQueue = null;
		this.inQueue = null;
		this.outQueue = null;
	}
	private void resetLiv(boolean remote){
		if(remote){
			heart_remote=0;
		}else{
			heart_local=0;
		}
	}
	void doTick(){
		locker.lock();
		try{
		    if(isConnected()){
		        this.doTickImp();
		    }
		}finally{
		    if(isConnected()){
		        tickFuture = TickHelper.timeout(tickTask, TICK_INTERVAL, TimeUnit.MILLISECONDS);
		    }
			locker.unlock();
		}
	}
	private void doTickImp() {
		heart_remote++;
		heart_local++;
		if(heart_remote>INTERVAL_ALIVE){
			this.closeInfo = "RemoteDead";
			this.close();
			return;
		}
		if(heart_local>INTERVAL_HEART){
			heart_local=0;
			this.sendFrame(new LIVFrame(Seq.next(seq.getLastInNum())));
		}
//		if(!synQueue.isEmpty() || !inQueue.isEmpty()){
//		    System.out.println(seq.num()+" "+seq.getLastInNum()+" ->"+Arrays.toString(inQueue.toArray()) + " \n=> "+Arrays.toString(synQueue.toArray()));
//		}
		retry_interval++;
		if(retry_interval%2==0){
			Iterator<Frame> it = synQueue.iterator();
			 while(isConnected() && it.hasNext()){
				 try {
					reSendFrame(it.next());
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			 }
		}
	}
	/**
	 * 接受到数据
	 * @param packet
	 */
	void handData(DatagramPacket packet){
//		Frame frame = Frame.parse(packet.getData(),packet.getOffset(),packet.getLength());
	    Frame frame = Frame.parse(packet.getData());
		if(frame==null){
			return;
		}
		locker.lock();
		try{
			handFrame(frame, packet.getSocketAddress());
		}catch(Exception err){
			err.printStackTrace();
		}finally{
			locker.unlock();
		}
	}
	protected void handFrame(Frame frame, SocketAddress addr){
//		System.out.println(socket.name()+"_recive:"+frame.type()+"-"+ " "+frame + "   from:"+addr);
//		System.out.println("hand-"+frame.seq()+" , "+seq.getLastInNum());
		this.resetLiv(true);
		if(frame instanceof SYNFrame){
			if(this.stat==UxSocketStat.INIT){
				this.stat = UxSocketStat.WORK;
				this.address = addr;
				this.seq.initNum();
				this.seq.setLastInNum(frame.seq());
				this.sendFrameImp(new ACKFrame(this.seq.num(), frame.seq()));
				this.onOpen();
			}
		}else if(frame instanceof FINFrame){
			if(this.stat==UxSocketStat.WORK||this.stat==UxSocketStat.CONNECT){
				this.sendFrameImp(new ACKFrame(this.seq.num(), frame.seq()));
				this.stat = UxSocketStat.CLOSED;
				this.closeInfo = "RemoteClose";
				this.closeImp();
				return;
			}
		}else if(frame instanceof DATFrame){
			int compareRet = Seq.compare(frame.seq(), Seq.next(seq.getLastInNum()));
			if(compareRet==0){
				seq.setLastInNum(frame.seq());
				reciveData((DATFrame)frame);
				if(inQueue.isEmpty()==false){
					Iterator<Frame> it = inQueue.iterator();
					while(it.hasNext()){
						Frame f = it.next();
						if(f.seq()==frame.seq()){
							it.remove();
						}else if(Seq.compare(f.seq(), Seq.next(seq.getLastInNum()))==0){
							it.remove();
							seq.setLastInNum(f.seq());
							reciveData((DATFrame)f);
						}
					}
				}
				this.sendAck();
			}else if(compareRet>0){
				boolean add = true;
				if(inQueue.isEmpty()){
//					System.out.println("add-frame:"+frame.seq()+"_in("+seq.getLastInNum()+")");
					inQueue.add(frame);
					this.sendAck();
				}else if(inQueue.size()<MaxInQueueSize){
					Iterator<Frame> it = inQueue.iterator();
					while(it.hasNext()){
						if(it.next().seq()==frame.seq()){
							add = false;
							break;
						}
					}
					if(add){
//						System.out.println("add-frame:"+frame.seq()+"_in("+seq.getLastInNum()+")");
						inQueue.add(frame);
						Collections.sort(inQueue, Frame.Comparator);
						this.sendAck();
					}else{
						//drop
					}
				}else{
					add = false;
					//drop
				}
			}
		}else if(frame instanceof LIVFrame){
			
		}else if(frame instanceof EAKFrame){
			boolean deledSyn = false;
			int[] acks = ((EAKFrame)frame).getACKs();
			Iterator<Frame> it = synQueue.iterator();
			while(it.hasNext()){
				Frame f = it.next();
				if(f.seq()==frame.ack()){
					it.remove();
					deledSyn = true;
				}else{
					for(int i=0;i<acks.length;i++){
						if(f.seq()==acks[i]){
							it.remove();
							deledSyn = true;
							break;
						}
					}
				}
			}
			 int in_last_seq = frame.ack();
			 int out_last_seq = acks[acks.length-1];
			 it = synQueue.iterator();
			 while(it.hasNext()){
				 Frame f = it.next();
				 if(Seq.compare(f.seq(), in_last_seq)<0){
					 it.remove();
					 deledSyn = true;
					 continue;
				 }
			 }
			 if(deledSyn){
				 sendOutQueue();
			 }
			 
			 it = synQueue.iterator();
			 while(it.hasNext()){
				 Frame f = it.next();
				 if(Seq.compare(in_last_seq, f.seq())<0 && Seq.compare(out_last_seq, f.seq())>0){
					 try {
						reSendFrame(f);
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
				 }
			 }
		}
		if(frame.ack()>=0){
			Iterator<Frame> it = synQueue.iterator();
			boolean deledSyn = false;
			while(it.hasNext()){
				Frame f = it.next();
				if(Seq.compare(f.seq(),frame.ack())<=0){
				    it.remove();
				    deledSyn = true;
				 }
			}
			if(deledSyn){
				sendOutQueue();
			}
		}
	}
	/**
	 * 重传数据帧
	 * @param f
	 * @throws IOException 
	 */
	protected void reSendFrame(Frame f) throws IOException {
		if(f.reWriteNum()>MaxRewriteNum){
			this.closeInfo = "MaxRetry";
			this.close();
			throw new IOException("Socket Closed When max-retry.");
		}else{
			f.reWriteNum();
//			System.out.println("re-write:"+f);
			sendFrame(f);
		}
	}
	private void sendAck(){
		if(inQueue.isEmpty()){
			sendFrameImp(new ACKFrame(Seq.next(seq.getLastInNum()), seq.getLastInNum()));
		}else{
			synchronized (inQueue) {
				int[] acks = new int[inQueue.size()];
				for(int i=0;i<acks.length;i++){
					acks[i]=inQueue.get(i).seq();
				}
				sendFrameImp(new EAKFrame(Seq.next(seq.getLastInNum()), seq.getLastInNum(), acks));
			}
		}
	}
	
//	public void sendData(DatagramPacket packet) throws IOException{
//		DATFrame frame = new DATFrame(seq.next(), -1, packet.getData(), packet.getOffset(), packet.getLength());
//		sendSynFrame(frame);
//	}
	/**
	 * 发送数据，网络拥堵（大量数据报未被确认）时，会引发阻塞，超时会引起关闭
	 * @param buf
	 * @throws IOException
	 */
	public void sendData(byte[] buf) throws IOException{
		this.sendData(buf, 0, buf.length);
	}
	/**
	 * 发送数据，网络拥堵（大量数据报未被确认）时，会引发阻塞，超时会引起关闭
	 * @param buf
	 * @param offset
	 * @param length
	 * @throws IOException
	 */
	public void sendData(byte[] buf, int offset, int length) throws IOException{
		int s = length-offset;
		 if(s<1 )return;
		 if(s<225){
			 sendSynFrame(new DATFrame(seq.next(), -1, buf, offset, length));
			 return;
		 }
		 int totalBytes = 0;
		 while (totalBytes < length) {
			 int writeBytes = Math.min(MaxFrameSize - Frame.HEADER_LEN, length - totalBytes);
			 sendSynFrame(new DATFrame(seq.next(),
			 seq.getLastInNum(), buf, offset + totalBytes, writeBytes));
			 totalBytes += writeBytes;
		 }
	}
	private synchronized void sendOutQueue(){
			while(!outQueue.isEmpty() && synQueue.size()<MaxSynQueueSize){
				if(!isConnected()){
					break;
				}
				Frame fo = outQueue.pollFirst();
				if(fo!=null){
					if(synQueue.offer(fo)){
						sendFrame(fo);
					}else{
						try {
							outQueue.putFirst(fo);
							break;
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
	}
	/**
	 * 发送需要同步的数据帧
	 * @param f
	 * @throws IOException 
	 */
	protected synchronized void sendSynFrame(Frame f) throws IOException{
		if(!isConnected()){
			throw new IOException("Socket had Closed.");
		}
		sendOutQueue();
		if(synQueue.offer(f)){
			sendFrame(f);
		}else{
			boolean ok = true;
			try {
				ok = outQueue.offer(f, SendTimeOut, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(!ok){
				this.closeInfo = "MaxSyncWaite";
				this.close();
				throw new IOException("IoException:max-syn-waite("+synQueue.size()+"),then closed!");
			}
		}
	}
	/**
	 * 发送数据帧
	 * @param f
	 */
	protected void sendFrame(Frame f){
//		  if(!(f instanceof ACKFrame) && !(f instanceof EAKFrame)){
//		     if(seq.getActNum()>0){
//		    	 f.ack(seq.getLastInNum());
//		     }
//		  }
		  f.ack(seq.getLastInNum());
		  sendFrameImp(f);
	}
//	private ArrayList<Frame> frames = new ArrayList<Frame>();
//	private boolean first = true;
	/**
	 * 写出数据的实现
	 * @param f
	 */
	protected void sendFrameImp(Frame f){
	    /*if(!first && Math.random()>0.6 && frames.size()<=8){
	        synchronized(frames){
	            frames.add(f);
	        }
	        this.resetLiv(false);
	        return;
	    }
	    first = false;
	    if(frames.size()>8 || Math.random()<0.05){
	        synchronized (frames) {
    	        Collections.shuffle(frames);
    	        for(Frame n:frames){
    	            byte[] bin = n.getBytes();
    	            if(Math.random()>0.7){
    	                bin[0] = 0;
    	            }
    	            DatagramPacket packet = new DatagramPacket(bin, bin.length, address);
    	            try {
    	                socket.send(packet);
    	                this.resetLiv(false);
    	            } catch (IOException e) {
    	                e.printStackTrace();
    	                this.closeInfo = "CloseBySendIoException";
    	                this.close();
    	            }
    	        }
    	        frames.clear();
	        }
	    }*/
	    
//		System.out.println(socket.name()+"_send:"+f.type()+"-"+ "   "+f);
	    byte[] bin = f.getBytes();
		DatagramPacket packet = new DatagramPacket(bin, bin.length, address);
		try {
			socket.send(packet);
			this.resetLiv(false);
		} catch (IOException e) {
			e.printStackTrace();
			this.closeInfo = "CloseBySendIoException";
			this.close();
		}
	}
}
