package u14.udpx.frames
{
	public class FINFrame extends Frame
	{
		public function FINFrame(seqn:int=-1)
		{
			init(FIN_FLAG, seqn, HEADER_LEN);
		}
		override public function type():String
		{
			return "FIN";
		}
	}
}