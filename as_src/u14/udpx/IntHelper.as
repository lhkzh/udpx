package u14.udpx
{
	import flash.utils.ByteArray;

	public final class IntHelper
	{
		private static var bytes:ByteArray = new ByteArray();
		public static function byte(n:int):int{
			bytes.position=0;
			bytes.writeByte(n);
			bytes.position=0;
			return bytes.readByte();
		}
	}
}