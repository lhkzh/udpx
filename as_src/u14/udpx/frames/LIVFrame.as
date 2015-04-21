package u14.udpx.frames
{
	public class LIVFrame extends Frame
	{
		public function LIVFrame(seqn:int=-1)
		{
			init(LIV_FLAG, seqn, HEADER_LEN);
		}
		
		override public function type():String
		{
			return "LIV";
		}
	}
}