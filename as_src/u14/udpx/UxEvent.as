package u14.udpx
{
	import flash.events.Event;
	
	public class UxEvent extends Event
	{
		public static const IOERROR:String = "ioerror";
		public static const CLOSE:String = "close";
		public static const CONNECT:String = "connect";
		public static const DATA:String = "data";
		
		public var data:*;
		
		public function UxEvent(type:String, data:*=null)
		{
			super(type);
			this.data = data;
		}
	}
}