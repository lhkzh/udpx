package u14.udpx;

import java.net.DatagramPacket;

/**
 * udp-socket 数据侦听器
 * @author zhangheng
 */
public interface UdpListener {
	public void handData(UdpSocket socket, DatagramPacket packet);
}
