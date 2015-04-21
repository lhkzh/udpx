package u14.udpx.frames
{
	import flash.utils.ByteArray;

	public class EAKFrame extends Frame
	{
		public function EAKFrame(seqn:int=-1, ackn:int=-1,  acks:Vector.<int>=null)
		{
			init(EAK_FLAG, seqn, HEADER_LEN + acks.length);
			this.ack = (ackn);
			_acks = acks;
		}
		
		override public function type():String
		{
			return "EAK";
		}
		
		override public function getBytes():ByteArray
		{
			var buf:ByteArray = super.getBytes();
			for(var i:int=0;i<buf.length;i++){
				buf.writeByte(_acks[i]&0xFF);
			}
			return buf;
		}
		
		public function getACKs():Vector.<int>
		{
			return _acks;
		}
		
		override protected function parseBytes(buffer:ByteArray, off:int, len:int):void
		{
			super.parseBytes(buffer, off, len);
			_acks = new Vector.<int>(length() - HEADER_LEN);
			for (var i:int = 0; i < _acks.length; i++) {
				_acks[i] = buffer[off + HEADER_LEN + i];//(buffer[off + HEADER_LEN + i] & 0xFF);
			}
		}
		
		private var _acks:Vector.<int>;
	}
}