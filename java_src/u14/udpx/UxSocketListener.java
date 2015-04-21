package u14.udpx;

/**
 * socket状况侦听
 * @author zhangheng
 */
public interface UxSocketListener {
	/**
	 * 当收到数据
	 * @param socket
	 * @param data
	 */
	public void onData(UxSocket socket, byte[] data);
	/**
	 * 当socket连接成功/接受到连接
	 * @param socket
	 */
	public void onOpen(UxSocket socket);
	/**
	 * 当socket关闭
	 * @param socket
	 */
	public void onClose(UxSocket socket);
}
