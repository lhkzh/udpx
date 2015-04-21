package
{
	import flash.display.Sprite;
	import flash.events.Event;
	import flash.net.DatagramSocket;
	import flash.utils.ByteArray;
	import flash.utils.setTimeout;
	
	import u14.udpx.Seq;
	import u14.udpx.UxEvent;
	import u14.udpx.UxSocket;
	import u14.udpx.frames.SYNFrame;
	
	public class Demo extends Sprite
	{
		private var socket:UxSocket;
		public function Demo()
		{
//			var socket:DatagramSocket = new DatagramSocket();
//			socket.connect("127.0.0.1", 8008);
//			for(var i=0;i<20;i++){
//				var bytes:ByteArray = new ByteArray();
//				bytes.writeUTFBytes("hi-server@"+i);
//				socket.send(bytes);
//			}
			socket = new UxSocket();
			socket.addEventListener(UxEvent.CONNECT, onConnect);
			socket.addEventListener(UxEvent.IOERROR, onError);
			socket.addEventListener(UxEvent.CLOSE, onClose);
			socket.addEventListener(UxEvent.DATA, onData);
			socket.connect("127.0.0.1", 8008);
		}
		
		protected function onData(event:UxEvent):void
		{
			var bytes:ByteArray = event.data;
			trace("on-data:"+bytes);
		}
		protected function onClose(event:UxEvent):void
		{
			trace("connect-close:"+event.data);
		}
		protected function onError(event:UxEvent):void
		{
			trace("connect-error:"+event.data);
		}
		protected function onConnect(event:UxEvent):void
		{
			trace("connect-ok");
			send();
			setTimeout(send,1000);
		}
		
		private function send():void
		{
			for(var i:int=0;i<100;i++){
				socket.sendData(toBuf("rp-msg-fjpiwhpqirupq83ww.biblegateway.com/passage/?search=Psalm+62%3A5-12&version=NLTCommonww.biblegateway.com/passage/?search=Psalm+62%3A5-12&version=NLTCommon people are as worthlerpss as a puff of wind,and the powerful are not w people are as worthrpless as a puff of wind,and the powerful are not w7897408qyp8hfpuihapiyuu89r7098270598y708ehyphuihaufhuwnvm,nznknv;kaj;iamspeankinghanwensimidaoooaifwjjjjxxxxooooppppkkkkjpqiejporhpquhudhfahhelloworld,imzhanghengsimida-jdiqiurhas~~@@##"+i));
			}			
		}
		
		private function toBuf(s:String):flash.utils.ByteArray
		{
			var b:ByteArray = new ByteArray();
			b.writeUTFBytes(s);
			b.position = 0;
			return b;
		}
	}
}