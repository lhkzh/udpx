package u14.udpx;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;

/**
 * udp-工作socket
 * @author zhangheng
 */
public class UdpSocket implements Runnable {

	private static int counter=0;
	
	private DatagramSocket channel;
	private UdpListener delegate;
	private String name = "UdpSocket:"+(counter++);
	private volatile Thread reciveThread;
	private SocketAddress address;
	
	private UdpWorkThread workThread;
	/**
	 * 使用数据线程代理
	 */
	private boolean workAble;
	
	/**
	 * 随机绑定端口-客户端模式
	 * @return
	 */
	public static UdpSocket client(){
		try {
			return new UdpSocket(new InetSocketAddress(0));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public UdpSocket(){
		this((UdpListener)null);
	}
	public UdpSocket(UdpListener delegate){
		this.delegate = delegate;
		this.workThread = new UdpWorkThread(this);
		this.workThread.listen(delegate);
	}
	public UdpSocket(InetSocketAddress addr) throws IOException{
		this(addr, null);
	}
	public UdpSocket(InetSocketAddress addr, UdpListener delegate) throws IOException{
		this.delegate = delegate;
		this.workThread = new UdpWorkThread(this);
		this.workThread.listen(delegate);
		if(addr!=null){
			this.bind(addr);
		}
	}
	
	public UdpSocket name(String n){
		this.name = n;
		if(reciveThread!=null){
			reciveThread.setName(n+"_recive");
		}
		return this;
	}
	public String name(){
		return this.name;
	}
	public SocketAddress address(){
		return address;
	}
	
	public UdpSocket listen(UdpListener delegate){
		this.delegate = delegate;
		this.workThread.listen(delegate);
		return this;
	}
	public UdpSocket bind(int port) throws IOException{
		return this.bind(port, false);
	}
	private boolean isIPv6(String addr){
		return Pattern.compile(
                "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$")
                .matcher(addr).matches();
	}
	public UdpSocket bind(int port, boolean ipv6) throws IOException{
		InetSocketAddress isa = null;
		if (ipv6)
		{
			InetAddress[] addresses = InetAddress.getAllByName("localhost");
			for (InetAddress addr : addresses)
			{
				if (!addr.isLoopbackAddress() && isIPv6(addr.getHostAddress()))
				{
					isa = new InetSocketAddress(addr, port);
					break;
				}
			}
			if (isa == null)
				isa = new InetSocketAddress(port);//FIXME does this set an address? Should this method accept an address parameter?
		}
		else
			isa = new InetSocketAddress((InetAddress) null, port);
		return this.bind(isa);
	}
	public UdpSocket bind(SocketAddress addr) throws IOException{
//		if(delegate==null){
//			throw new IllegalAccessError("Must Set Delegate Before Bind");
//		}
		if(channel!=null && !channel.isClosed()){
			throw new IllegalAccessError("Had a alive Binded");
		}
		channel = new DatagramSocket(addr);
		channel.setSoTimeout(1000);
		address = addr;
		return this;
	}
	/**
	 * 发送数据包
	 * @param p
	 * @return
	 * @throws IOException
	 */
	public UdpSocket send(DatagramPacket p) throws IOException{
		if(channel==null||channel.isClosed()){
			if(address==null){
				throw new IOException("You must bind a address or use client model(UdpSocket.client())!");
			}
			throw new IOException("Socket had closed!");
		}
		channel.send(p);
		return this;
	}
	/**
	 * 发送数据包
	 * @param buf
	 * @param address
	 * @return
	 * @throws IOException
	 */
	public UdpSocket send(byte[] buf, SocketAddress address) throws IOException{
		return send(new DatagramPacket(buf, buf.length, address));
	}
	/**
	 * 发送数据包
	 * @param buf
	 * @param host
	 * @param port
	 * @return
	 * @throws IOException
	 */
	public UdpSocket send(byte[] buf, String host, int port) throws IOException{
		return send(new DatagramPacket(buf, buf.length, new InetSocketAddress(host, port)));
	}
	/**
	 * 开始工作
	 * @return
	 */
	public synchronized UdpSocket start(){
		if(reciveThread==null){
			reciveThread = new Thread(this);
			reciveThread.setName(this.name);
			if(this.channel!=null && channel.isBound()==false){
				try {
					this.bind(address);
				} catch (Exception e) {
					this.stop();
					throw new RuntimeException(e);
				}
			}
			reciveThread.start();
			if(workAble){
				workThread.listen(this.delegate);
				workThread.start();
			}
		}
		return this;
	}
	/**
	 * 停止工作
	 * @return
	 */
	public synchronized UdpSocket stop(){
		if(reciveThread!=null){
			workThread.stop();
			reciveThread = null;
			channel.close();
		}
		return this;
	}
	/**
	 * 是否在工作中
	 * @return
	 */
	public boolean isAlive(){
		return reciveThread!=null;
	}
	public void useWorkThread(boolean b){
		this.workAble = b;
		if(this.workAble){
			if(isAlive() && workThread.isAlive()==false){
				workThread.start();
			}
		}else{
			workThread.stop();
		}
	}
	@Override
	public void run() {
		byte[] buf = new byte[65535];
		byte[] buf_empty = new byte[0];
		while(isAlive()){
			DatagramPacket packet = new DatagramPacket(buf,buf.length);
			try {
				channel.receive(packet);
			}catch(SocketTimeoutException et){
				continue;
			}catch (IOException e) {
				e.printStackTrace();
				break;
			}
			if(delegate!=null){
				if(packet.getLength()==0){
					packet.setData(buf_empty);
				}else{
					byte[] data = new byte[packet.getLength()];
					System.arraycopy(buf, packet.getOffset(), data, 0, packet.getLength());
					packet.setData(data);
				}
				onRecivePacket(packet);
			}
		}
		this.stop();
	}
	private void onRecivePacket(DatagramPacket packet) {
		if(workAble){
			workThread.offer(packet);
		}else if(delegate!=null){
			delegate.handData(this, packet);
		}
	}
	public DatagramSocket socket() {
		return channel;
	}

}
