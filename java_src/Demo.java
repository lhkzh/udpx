package u14.udpx;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Demo {
	private static UxServer server;
	public static void testServer() throws IOException{
		server  = new UxServer().bind(new InetSocketAddress(8008)).listen(new UxSocketListener() {
			@Override
			public void onOpen(UxSocket socket) {
				System.out.println("on-socket-open:"+socket.address());
				try {
					socket.sendData("https://www.biblegateway.com/passage/?search=Psalm+62%3A5-12&version=NLTCommon people are as worthless as a puff of wind,and the powerful are not what they appear to be.Don’t make your living by extortiondon’t make it the center of your life.God has spoken plainlyPower, O God, belongs to you".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			@Override
			public void onData(UxSocket socket, byte[] data) {
				System.out.println("on-socket-data:"+socket.address()+"->"+new String(data));
				if(new String(data).startsWith("rp")){
					try {
						socket.sendData(data);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				for(UxSocket s:	server.clients()){
					try {
						s.sendData(data);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			@Override
			public void onClose(UxSocket socket) {
				System.out.println("on-socket-close:"+socket.address()+ socket.closeInfo());
			}
		});
		System.out.println("server");
	}
	public static void testClient() throws IOException{
		UxSocket socket = new UxSocket();
		socket.listen(new UxSocketListener() {
			@Override
			public void onOpen(UxSocket socket) {
				System.out.println("on-open");
				try {
					socket.sendData("hi123456".getBytes());
					socket.sendData("woabc".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			@Override
			public void onData(UxSocket socket, byte[] data) {
				System.out.println("on-data:"+new String(data));
			}
			@Override
			public void onClose(UxSocket socket) {
				System.out.println("on-close-"+ socket.closeInfo());
			}
		})
		.connect(new InetSocketAddress("127.0.0.1",8008));
//		.connect(new InetSocketAddress("s1.gz.1251014155.clb.myqcloud.com", 8008));
		try {
			Thread.sleep(1000L);
			for(int i=0;i<1000;i++){
				socket.sendData(("rp-msgat:"+i).getBytes());
				Thread.sleep(10L);
			}
			Thread.sleep(300000L);
			System.exit(0);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void main(String[] args) throws IOException {
		boolean serverAble = args.length==1 && args[0].equals("1");
		if(args.length==0)serverAble=true;
		if(serverAble){
			testServer();
		}else{
			testClient();
		}
	}
}
