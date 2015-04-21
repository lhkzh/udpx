package u14.udpx.frames
{
	import flash.utils.ByteArray;

	public class DATFrame extends Frame
	{
		public function DATFrame(seqn:int=-1, ackn:int=-1, b:ByteArray=null, off:int=0, len:int=0)
		{
			init(ACK_FLAG, seqn, HEADER_LEN);
			this.ack=(ackn);
			if(b!=null){
				_data = b;
				_dataOffset = off;
				_dataLength = len;
//				_data = new byte[len];
//				System.arraycopy(b, off, _data, 0, len);
			}
		}
		
		override public function length():int
		{
			return (_dataLength>0?_dataLength:_data.length) + super.length();
		}
		
		override public function type():String
		{
			return "DAT";
		}
		
		public function getData():ByteArray
		{
			return _data;
		}
		
		override public function getBytes():ByteArray
		{
			var buffer:ByteArray = super.getBytes();
			buffer.writeBytes(_data, _dataOffset, _dataLength);
			return buffer;
		}
		
		override protected function parseBytes(buffer:ByteArray, off:int, len:int):void
		{
			super.parseBytes(buffer, off, len);
			_data = new ByteArray();//new byte[len - HEADER_LEN];
			off = off+HEADER_LEN;
			len = len>0?len-HEADER_LEN:0;
			_data.writeBytes(buffer, off, len);
			_data.position = 0;
		}
		private var _dataOffset:int=0;
		private var _dataLength:int=0;
		private var _data:ByteArray;
	}
}