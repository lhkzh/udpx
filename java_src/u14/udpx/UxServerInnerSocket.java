package u14.udpx;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.TimeoutException;

import u14.udpx.frames.DATFrame;

/**
 * 服务端接受的socket
 * @author zhangheng
 */
class UxServerInnerSocket extends UxSocket{

	private UxServer parent;
	public UxServerInnerSocket(UxServer parent, SocketAddress addr){
		super();
		this.parent = parent;
		this.socket = parent.socket();
	}

	@Override
	public synchronized void connect(SocketAddress addr, int timeout)
			throws IOException, TimeoutException {
		throw new IllegalAccessError("Not implements!");
	}

	@Override
	protected synchronized void closeImp() {
		this.socket = null;
		removeFromParent();
	}
	
	private void removeFromParent(){
		if(this.parent!=null){
			this.parent.remove(this);
			this.parent=null;
		}
	}

	@Override
	public synchronized void close() {
		if(isConnected()){
			removeFromParent();
			super.close();
		}
	}

	@Override
	protected void reciveData(DATFrame frame) {
		if(this.parent!=null){
			this.parent.onReciveData(this, frame.getData());
			super.reciveData(frame);
		}
	}
	
}
