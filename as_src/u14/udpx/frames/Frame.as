package u14.udpx.frames
{
	import flash.utils.ByteArray;
	
	import u14.udpx.IntHelper;
	import u14.udpx.Seq;

	public  class Frame
	{
		public static function Comparator(a:Frame, b:Frame):int{
			return Seq.compare(a.seq(), b.seq());
		}
		
		public static const SYN_FLAG:int = IntHelper.byte(0x80);
		public static const ACK_FLAG:int = IntHelper.byte(0x40);
		public static const EAK_FLAG:int = IntHelper.byte(0x20);
		public static const LIV_FLAG:int = IntHelper.byte(0x08);
		//    public static byte CHK_FLAG = IntHelper.byte(0x04);
		public static const FIN_FLAG:int = IntHelper.byte(0x02);
		
		public static const HEADER_LEN:int = 4;
		
		private var _flag:int;
		private var _len:int;
		private var _seq:int;
		private var _ack:int=-1;
		
		private var _reWriteNum:int=0;
		
		public function Frame()
		{
		}
		
		public function type():String{
			return "ABS";
		}
		
		public function flag():int{
			return _flag;
		}
		public function seq():int{
			return _seq;
		}
		public function length():int{
			return _len;
		}
		public function get ack():int{
			return _ack;
		}
		public function set ack(n:int):void{
			_ack = n;
		}
		
		public function incrReWriteNum():int{
			_reWriteNum++;
			return _reWriteNum;
		}
		public function reWriteNum():int{
			return _reWriteNum;
		}
		
		public function getBytes():ByteArray
		{
			var buffer:ByteArray = new ByteArray();
//			buffer[0] = (byte) (flag & 0xFF);
//			buffer[1] = (byte) (len & 0xFF);
//			buffer[2] = (byte) (seq & 0xFF);
//			buffer[3] = (byte) (ack & 0xFF);
			buffer.writeByte(_flag&0xFF);
			buffer.writeByte(_len&0xFF);
			buffer.writeByte(_seq&0xFF);
			buffer.writeByte(_ack&0xFF);
			return buffer;
		}
		public function toString():String
		{
			return type() +
				" [" +
				" SEQ = " + seq() +
				", ACK = " + ((ack >= 0) ? ""+ack : "N/A") +
				", LEN = " + length() +
				" ]";
		}
		protected function init(flag:int, seq:int, len:int):void
		{
			this._flag = flag;
			this._seq = seq;
			this._len = len;
		}
		protected function parseBytes(buffer:ByteArray, off:int, len:int):void
		{
			this._flag = buffer[off];//(buffer[off] & 0xFF);
			this._len = buffer[off+1];//(buffer[off+1] & 0xFF);
			this._seq = buffer[off+2];//(buffer[off+2] & 0xFF);
			this._ack = buffer[off+3];//(buffer[off+3] & 0xFF);
		}
		public static function parse(bytes:ByteArray, off:int=0, len:int=-1):Frame
		{
			if(len==-1){
				len = bytes.length;
			}
			if (len < HEADER_LEN) {
				//            throw new IllegalArgumentException("Invalid segment");
				return null;
			}
			var segment:Frame = null;
			var flags:int = IntHelper.byte(bytes[off]&0xFF);
			if ((flags & SYN_FLAG) != 0) {
				segment = new SYNFrame();
			}
			else if ((flags & LIV_FLAG) != 0) {
				segment = new LIVFrame();
			}
			else if ((flags & EAK_FLAG) != 0) {
				segment = new EAKFrame();
			}
			else if ((flags & FIN_FLAG) != 0) {
				segment = new FINFrame();
			}
			else if ((flags & ACK_FLAG) != 0) { /* always process ACKs or Data segments last */
				if (len == HEADER_LEN) {
					segment = new ACKFrame();
				}
				else {
					segment = new DATFrame();
				}
			}
			
			if (segment == null) {
				throw new Error("Invalid segment");
			}
			
			segment.parseBytes(bytes, off, len);
			return segment;
		}
	}
}