package u14.udpx;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import u14.udpx.frames.Frame;
import u14.udpx.frames.SYNFrame;

/**
 * 服务端
 * @author zhangheng
 */
public class UxServer {

	private UdpSocket socket;
	private ConcurrentHashMap<SocketAddress, UxSocket> map;
	private UxSocketListener delegate;
	
	public UxServer(){
		map = new ConcurrentHashMap<SocketAddress, UxSocket>();
		socket = new UdpSocket().listen(new UdpListener() {
			@Override
			public void handData(UdpSocket socket, DatagramPacket packet) {
				UxServer.this.onReciveData(packet);
			}
		}).name("UxServer_recive");
		socket.useWorkThread(true);
	}
	
	public int size(){
		return map.size();
	}
	public Collection<UxSocket> clients(){
		return map.values();
	}
	public UxServer listen(UxSocketListener listener){
		this.delegate = listener;
		return this;
	}
	
	public UxServer bind(InetSocketAddress addr) throws IOException{
		this.socket.bind(addr).start();
		return this;
	}
	private void onReciveData(DatagramPacket packet) {
		UxSocket sk = map.get(packet.getSocketAddress());
		if(sk==null){
			Frame frame = Frame.parse(packet.getData(),packet.getOffset(),packet.getLength());
			if(frame instanceof SYNFrame && accept(packet)){
				sk = new UxServerInnerSocket(this, packet.getSocketAddress());
//				System.out.println("server-new-socket:"+sk.address+"  #  "+frame);
				map.put(packet.getSocketAddress(), sk);
				sk.handFrame(frame, packet.getSocketAddress());
				if(this.delegate!=null){
					this.delegate.onOpen(sk);
				}
			}
		}else{
			sk.handData(packet);
		}
	}
	
	/**
	 * 是否接受packet的链接确认
	 * @param packet
	 * @return
	 */
	protected boolean accept(DatagramPacket packet){
		return true;
	}
	
	public synchronized void stop(){
		Object[] arr = map.values().toArray();
		for(Object n : arr){
			((UxSocket) n).close();
		}
		map.clear();
		socket.stop();
	}
	
	public UdpSocket socket() {
		return socket;
	}
	void remove(UxServerInnerSocket socket) {
		if(map.remove(socket.address())!=null){
			if(this.delegate!=null){
				this.delegate.onClose(socket);
			}
		}
	}
	void onReciveData(UxServerInnerSocket socket, byte[] data) {
		if(this.delegate!=null){
			this.delegate.onData(socket, data);
		}
	}
	
}
