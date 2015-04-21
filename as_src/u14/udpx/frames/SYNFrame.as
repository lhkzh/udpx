package u14.udpx.frames
{
	public class SYNFrame extends Frame
	{
		public function SYNFrame(seqn:int=-1)
		{
			init(SYN_FLAG, seqn, HEADER_LEN);
		}
		
		override public function type():String
		{
			return "SYN";
		}
	}
}