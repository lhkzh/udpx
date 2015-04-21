package u14.udpx
{
	public class Seq
	{
		private var _num:int=0;
		private var _lastInNum:int=0;
		
		public function Seq()
		{
		}
		
		public function next():int{
			return (_num = Seq.next(_num));
		}
		public function get num():int{
			return _num;
		}
		public function initNum():int{
			var n:int=Math.floor(Math.random()*MAX_SEQ/2);
			return (_num = n);
			//		return (num=0);
		}
		
		public function set lastInNum(n:int):void{
			_lastInNum = n;
		}
		public function get lastInNum():int{
			return _lastInNum;
		}
		
		private static const MAX_SEQ:int = 255;
		/**
		 * Computes the consecutive sequence number.
		 * @return the next number in the sequence.
		 */
		public static function next(n:int):int
		{
			return (n + 1) % MAX_SEQ;
		}
		/**
		 * Compares two sequence numbers.
		 * @return 0, 1 or -1 if the first sequence number is equal,
		 *         greater or less than the second sequence number.
		 *         (see RFC 1982).
		 */
		public static function compare(a:int, b:int):int
		{
			if (a == b) {
				return 0;
			}else if (((a < b) && ((b - a) > MAX_SEQ/2)) ||
				((a > b) && ((a - b) < MAX_SEQ/2))) {
				return 1;
			}else {
				return -1;
			}
		}
	}
}