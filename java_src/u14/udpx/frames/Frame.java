package u14.udpx.frames;

import java.util.Comparator;

import u14.udpx.Seq;

/**
 * [flag|headerLength|seq|ack]
 */
public abstract class Frame {

    public static final byte SYN_FLAG = (byte) 0x80;
    public static final byte ACK_FLAG = (byte) 0x40;
    public static final byte EAK_FLAG = (byte) 0x20;
    public static final byte LIV_FLAG = (byte) 0x08;
//    public static final byte CHK_FLAG = (byte) 0x04;
    public static final byte FIN_FLAG = (byte) 0x02;
    
    public static final byte HEADER_LEN = 4;
	
	private int flag;
	private int len;
	private int seq;
	private int ack=-1;
	
	private short reWriteNum=0;
	
	public Frame() {
	}
	public abstract String type();
	
	public int flag(){
		return flag;
	}
	public int seq(){
		return seq;
    }
	public int length(){
		return len;
	}
	public int ack(){
//		if ((ack & ACK_FLAG) == ACK_FLAG) {
//            return ack;
//        }
//        return -1;
		return ack;
	}
	public void ack(int n){
		ack = n;
	}
	
	public void incrReWriteNum(){
		reWriteNum++;
	}
	public int reWriteNum(){
		return reWriteNum;
	}
    public byte[] getBytes()
    {
        byte[] buffer = new byte[length()];
        buffer[0] = (byte) (flag & 0xFF);
        buffer[1] = (byte) (len & 0xFF);
        buffer[2] = (byte) (seq & 0xFF);
        buffer[3] = (byte) (ack & 0xFF);
        return buffer;
    }
    public String toString()
    {
        return type() +
        " [" +
        " SEQ = " + seq() +
        ", ACK = " + ((ack() >= 0) ? ""+ack() : "N/A") +
        ", LEN = " + length() +
        " ]";
    }
    protected void init(int flag, int seq, int len)
    {
        this.flag = flag;
        this.seq = seq;
        this.len = len;
    }
    protected void parseBytes(byte[] buffer, int off, int length)
    {
    	this.flag = (buffer[off] & 0xFF);
    	this.len = (buffer[off+1] & 0xFF);
    	this.seq = (buffer[off+2] & 0xFF);
    	this.ack = (buffer[off+3] & 0xFF);
    }
    
    public static Frame parse(byte[] bytes)
    {
        return Frame.parse(bytes, 0, bytes.length);
    }

    public static Frame parse(byte[] bytes, int off, int len)
    {
        if (len < HEADER_LEN) {
//            throw new IllegalArgumentException("Invalid segment");
        	return null;
        }
        Frame segment = null;
        int flags = bytes[off];
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
            throw new IllegalArgumentException("Invalid segment");
        }

        segment.parseBytes(bytes, off, len);
        return segment;
    }
    
    public static Comparator<Frame> Comparator = new Comparator<Frame>() {
		@Override
		public int compare(Frame o1, Frame o2) {
			return Seq.compare(o1.seq(), o2.seq());
		}
	};
}
