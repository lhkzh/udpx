package u14.udpx;


public class Seq {

	
	private volatile int num=0;
	private int lastInNum=0;
//	private int ackNum=0;
	
	public Seq() {
	}
	
	public int next(){
		return (num = next(num));
	}
	public int num(){
		return num;
	}
	public int initNum(){
		return (num = (new java.util.Random()).nextInt(MAX_SEQ/2));
//		return (num=0);
	}
	
	public int setLastInNum(int n){
		return (lastInNum = n);
	}
	public int getLastInNum(){
		return lastInNum;
	}
	
//	public int incrActNum(){
//		return ackNum++;
//	}
//	public int getActNum(){
//		return ackNum;
//	}
//	public int resetActNum(){
//		return ackNum=0;
//	}
	
	private static final int MAX_SEQ = 255;
	/**
	* Computes the consecutive sequence number.
	* @return the next number in the sequence.
	*/
	public static int next(int n)
	{
		return (n + 1) % MAX_SEQ;
	}
	/**
	* Compares two sequence numbers.
	* @return 0, 1 or -1 if the first sequence number is equal,
	*         greater or less than the second sequence number.
	*         (see RFC 1982).
	*/
	public static int compare(int a, int b)
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
