package u14.udpx.frames
{
	public class ACKFrame extends Frame
	{
		public function ACKFrame(seqn:int=-1, ackn:int=-1)
		{
			init(ACK_FLAG, seqn, HEADER_LEN);
			this.ack=(ackn);
		}
		
		override public function type():String
		{
			return "ACK";
		}
		
		
	}
}