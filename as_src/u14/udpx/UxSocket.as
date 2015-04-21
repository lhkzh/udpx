package u14.udpx
{
	import flash.desktop.NativeApplication;
	import flash.events.DatagramSocketDataEvent;
	import flash.events.Event;
	import flash.events.EventDispatcher;
	import flash.events.IOErrorEvent;
	import flash.events.ProgressEvent;
	import flash.events.TimerEvent;
	import flash.net.DatagramSocket;
	import flash.utils.ByteArray;
	import flash.utils.Timer;
	import flash.utils.clearTimeout;
	import flash.utils.setTimeout;
	
	import u14.udpx.frames.ACKFrame;
	import u14.udpx.frames.DATFrame;
	import u14.udpx.frames.EAKFrame;
	import u14.udpx.frames.FINFrame;
	import u14.udpx.frames.Frame;
	import u14.udpx.frames.LIVFrame;
	import u14.udpx.frames.SYNFrame;

	/**
	 * 建立连接成功
	 * */
	[Event(name="connect", type="u14.udpx.UxEvent")]
	/**
	 * 建立连接失败
	 * */
	[Event(name="ioerror", type="u14.udpx.UxEvent")]
	/**
	 * 连接关闭
	 * */
	[Event(name="close", type="u14.udpx.UxEvent")]
	/**
	 * 收到数据
	 * */
	[Event(name="data", type="u14.udpx.UxEvent")]
	
	public class UxSocket extends EventDispatcher
	{
		private static const MaxFrameSize:int = 255;
		private static const MaxInQueueSize:int = 32;
		private static const MaxSynQueueSize:int = 32;
		private static const MaxRewriteNum:int = 8;
		private static const INTERVAL_HEART:int = 50;
		private static const INTERVAL_ALIVE:int = 90;
		private static const SendTimeOut:int = 2500;
		
		private static const INIT:int = 0;
		private static const CONNECT:int = 1;
		private static const WORK:int = 2;
		private static const CLOSED:int = 3;
		
		private var seq:Seq;
		private var socket:DatagramSocket;
		private var address:String;
		private var port:int;
		
		private var stat:int=0;
		
		private var inQueue:Vector.<Frame>;
		private var synQueue:Vector.<Frame>;
		private var outQueue:Vector.<Frame>;
		
		private var closeInfo:String;
		private var timer:Timer;
		//多久未收到数据后断开
		private var heart_remote:int=0;
		//多久未写数据后，发alive
		private var heart_local:int=0;
		private var retry_interval:int=0;
		
		public function UxSocket()
		{
			seq = new Seq();
			timer = new Timer(100);
			timer.addEventListener(TimerEvent.TIMER, this.onIntervalTick);
			
			inQueue = new Vector.<Frame>();
			synQueue = new Vector.<Frame>();
			outQueue = new Vector.<Frame>();
			
			socket = new DatagramSocket();
			socket.receive();
			
		}
		
		protected function onIntervalTick(event:TimerEvent):void
		{
			heart_remote++;
			heart_local++;
			if(heart_remote>INTERVAL_ALIVE){
				this.closeInfo = "RemoteDead";
				this.close();
				return;
			}
			if(heart_local>INTERVAL_HEART){
				heart_local=0;
				this.sendFrame(new LIVFrame(Seq.next(seq.lastInNum)));
//				trace("check:"+(outQueue.length)+"_"+synQueue.length+"_"+inQueue.length);
			}
			retry_interval++;
			if(retry_interval%2==0){
				for each(var f:Frame in synQueue){
					reSendFrame(f);
					if(connected==false){
						break;
					}
				}
			}
		}
		private var connectTimerId:uint;
		public function connect(address:String, port:int):void{
			if(this.stat==CONNECT){
				return;
			}
			this.stat = CONNECT;
			this.address = address;
			this.port = port;
			this.sendFrameImp(new SYNFrame(seq.initNum()));
			this.socket.addEventListener(DatagramSocketDataEvent.DATA, onConnectData);
			connectTimerId=setTimeout(onConnectTimeout, 5000);
		}
		
		private function onConnectTimeout():void
		{
			this.socket.removeEventListener(DatagramSocketDataEvent.DATA, onConnectData);
			this.dispatchEvent(new UxEvent(UxEvent.IOERROR,address+":"+port+" Connect Timeout Error!"));
		}
		protected function onConnectData(event:DatagramSocketDataEvent):void
		{
			this.socket.removeEventListener(DatagramSocketDataEvent.DATA, onConnectData);
			var f:Frame = Frame.parse(event.data);
			if(f is ACKFrame){
				if(f.ack==seq.num){
					this.stat = WORK;
					this.seq.lastInNum = f.seq();
					this.onOpen();
				}
			}
			if(!connected){
				this.dispatchEvent(new UxEvent(UxEvent.IOERROR,address+":"+port+" Connect Error!"));
			}
			NativeApplication.nativeApplication.addEventListener(Event.EXITING, onAppExit);
		}
		
		protected function onAppExit(event:Event):void
		{
			this.close();
		}
		
		private function onOpen():void
		{
			clearTimeout(connectTimerId);
			synQueue.length=0;inQueue.length=0;outQueue.length=0;
			this.socket.addEventListener(DatagramSocketDataEvent.DATA, onPacketData);
			this.dispatchEvent(new UxEvent(UxEvent.CONNECT));	
			this.timer.start();
		}
		
		public function get connected():Boolean{
			return this.stat==WORK;
		}
		private function resetLiv(remote:Boolean):void{
			if(remote){
				heart_remote=0;
			}else{
				heart_local=0;
			}
		}
		private function reciveData(f:DATFrame):void{
			this.dispatchEvent(new UxEvent(UxEvent.DATA,f.getData()));
		}
		protected function onPacketData(event:DatagramSocketDataEvent):void
		{
			var frame:Frame = Frame.parse(event.data);
			if(frame==null)return;
			resetLiv(true);
			if(frame is FINFrame){
				if(stat==WORK){
					this.sendFrameImp(new ACKFrame(seq.num, frame.seq()));
					this.closeInfo = "RemoteClose";
					this.close();
				}
				return;
			}else if(frame is LIVFrame){
			}else if(frame is DATFrame){
				
				var compareRet:int = Seq.compare(frame.seq(), Seq.next(seq.lastInNum));
//				trace(compareRet+":"+frame.seq()+"_"+seq.lastInNum +
//				" : "+new String(frame.getBytes()));
				if(compareRet==0){
					seq.lastInNum = frame.seq();
					reciveData(DATFrame(frame));
					if(this.inQueue.length>0){
						for each(var f:Frame in inQueue){
							if(f.seq()==frame.seq()){
								delList.push(f);
							}else if(Seq.compare(f.seq(), Seq.next(seq.lastInNum))==0){
								delList.push(f);
								seq.lastInNum = frame.seq();
								reciveData(DATFrame(frame));
							}
						}
						delFrames(inQueue);
					}
					this.sendAck();
				}
				else if(compareRet>0){
					var add:Boolean = true;
					if(inQueue.length==0){
						//					System.out.println("add-frame:"+frame.seq()+"_in("+seq.getLastInNum()+")");
						inQueue.push(frame);
						this.sendAck();
					}else if(inQueue.length<MaxInQueueSize){
						for each(f in inQueue){
							if(f.seq()==frame.seq()){
								add = false;
								break;
							}
						}
						if(add){
							//						System.out.println("add-frame:"+frame.seq()+"_in("+seq.getLastInNum()+")");
							inQueue.push(frame);
							inQueue.sort(Frame.Comparator);
							this.sendAck();
						}else{
							//drop
						}
					}else{
						add = false;
						//drop
					}
				}
			}else if(frame is EAKFrame){
				var acks:Vector.<int> = (EAKFrame(frame)).getACKs();
				for each(f in synQueue){
					if(f.seq()==frame.ack){
						delList.push(f);
					}else{
						for each(var ack:int in acks){
							if(f.seq()==ack){
								delList.push(f);
								break;
							}
						}
					}
				}
				var delFlag:Boolean = delList.length>0;
				delFrames(synQueue);
				
				var in_last_seq:int = frame.ack;
				var out_last_seq:int = acks[acks.length-1];
				for each(f in synQueue){
					if(Seq.compare(f.seq(), in_last_seq)<0){
						delList.push(f);
						continue;
					}
				}
				delFlag = delFlag || delList.length>0;
				delFrames(synQueue);
				
				for each(f in synQueue){
					if(Seq.compare(in_last_seq, f.seq())<0 && Seq.compare(out_last_seq, f.seq())>0){
						reSendFrame(f);
						if(!connected){
							break;
						}
					}
				}
				if(connected && delFlag){
					sendOutQueue();
				}
			}
			if(frame.ack>=0){
				for each(f in synQueue){
					if(Seq.compare(f.seq(),frame.ack)<=0){
						delList.push(f);
					}
				}
				if(delList.length>0){
					delFrames(synQueue);
					sendOutQueue();
				}
			}
		}
		
		public function close():void{
			if(stat!=CLOSED){
				var s:int = stat;
				stat = CLOSED;
				clearTimeout(connectTimerId);
				this.socket.removeEventListener(DatagramSocketDataEvent.DATA, onPacketData);
				NativeApplication.nativeApplication.removeEventListener(Event.EXITING, onAppExit);
				this.timer.stop();
				if(s==WORK){
					this.sendFrameImp(new FINFrame(seq.next()));
					this.dispatchEvent(new UxEvent(UxEvent.CLOSE, closeInfo));
				}
			}
		}
		
		private function reSendFrame(f:Frame):void
		{
			if(connected==false)return;
			if(f.reWriteNum()>MaxRewriteNum){
				this.closeInfo = "MaxRetry";
				this.close();
			}else{
				f.reWriteNum();
				sendFrame(f);
			}
		}
		private function sendAck():void
		{
			if(inQueue.length==0){
				sendFrameImp(new ACKFrame(Seq.next(seq.lastInNum), seq.lastInNum));
			}else{
				var acks:Vector.<int> = new Vector.<int>(inQueue.length);
				for(var i:int=0;i<acks.length;i++){
					acks[i]=inQueue[i].seq();
				}
				sendFrameImp(new EAKFrame(Seq.next(seq.lastInNum), seq.lastInNum, acks));
			}
		}
		private var delList:Vector.<Frame> = new Vector.<Frame>();	
		private function delFrames(q:Vector.<Frame>):void
		{
			for each(var f:Frame in delList){
				var idx:int = q.indexOf(f);
				if(idx>=0){
					q.splice(idx,1);
				}
			}
			delList.length = 0;
		}
		public function sendData(buf:ByteArray, offset:int=0, length:int=0):void{
			length = length==0?buf.length:length;
			var s:int = length-offset;
			if(s<=0 )return;
			if(s<225){
				sendSynFrame(new DATFrame(seq.next(), -1, buf, offset, length));
				return;
			}
			var totalBytes:int = 0;
			while (totalBytes < length) {
				var writeBytes:int = Math.min(MaxFrameSize - Frame.HEADER_LEN, length - totalBytes);
				sendSynFrame(new DATFrame(seq.next(),
					seq.lastInNum, buf, offset + totalBytes, writeBytes));
				totalBytes += writeBytes;
			}
		}
		private function sendOutQueue():void{
			if(outQueue.length>0){
				while(synQueue.length<MaxSynQueueSize){
					if(!connected || outQueue.length==0){
						break;
					}
					var fo:Frame = outQueue.shift();
					synQueue.push(fo);
					sendFrame(fo);
				}
			}
		}
		protected function sendSynFrame(f:Frame):void{
			sendOutQueue();
			if(synQueue.length<MaxSynQueueSize){
				synQueue.push(f);
				sendFrame(f);
			}else{
				outQueue.push(f);
			}
		}
		protected function sendFrame(f:Frame):void{
			f.ack = seq.lastInNum;
			sendFrameImp(f);
		}
		protected function sendFrameImp(f:Frame):void{
			sendBuf(f.getBytes());
			resetLiv(false);
		}
		protected function sendBuf(buf:ByteArray):void{
			socket.send(buf, 0, 0, address, port);
		}
	}
}