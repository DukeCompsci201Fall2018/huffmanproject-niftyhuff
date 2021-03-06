import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT,  HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		
		while (true) {
		int inputStream = in.readBits(BITS_PER_WORD);
		if (inputStream==-1) break;
		String code = codings[inputStream];
		out.writeBits(code.length(),Integer.parseInt(code,2));
		}
		String code = codings[ALPH_SIZE];
		System.out.println(code);
		out.writeBits(code.length(),Integer.parseInt(code,2));
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {		
		//this is not strictly following writeup
		if (root.myLeft==null && root.myRight==null) {
			out.writeBits(1,  1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
			return;
		}
		if (root.myValue==0) {
			out.writeBits(1,  0);
			if(root.myLeft != null) writeHeader(root.myLeft, out);
			if(root.myRight != null) writeHeader(root.myRight, out);
		} 
		
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}

	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		if (root.myLeft!= null) codingHelper(root.myLeft,path + "0", encodings);
		if (root.myRight!= null) codingHelper(root.myRight,path + "1", encodings);
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int k = 0; k<counts.length;k+=1) {
			if(counts[k]>0)	pq.add(new HuffNode(k,counts[k],null,null));
		}
		System.out.println(pq.size());
		
		while (pq.size() >1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight,left,right); //try using naive value 0 first
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private int[] readForCounts(BitInputStream in) {
		int[] myTable = new int[ALPH_SIZE + 1];
		System.out.println(myTable.length);
		while (true) {
			int eachChunk = in.readBits(BITS_PER_WORD);
			if (eachChunk == -1) break;
			//System.out.println(eachChunk); //dont know how data input is, assuming it is number 1-257
			myTable[eachChunk] = myTable[eachChunk]+1;
		}
		System.out.println(myTable[ALPH_SIZE]);
		myTable[ALPH_SIZE] = 1;
		return myTable;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressesBits(root,in,out);
		out.close();
	}

	private void readCompressesBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
			if (bits == 0) current = current.myLeft;
				else current = current.myRight;
			
			if (current.myLeft == null && current.myRight == null) {
				if (current.myValue == PSEUDO_EOF) {
					break;
				} else {
					out.writeBits(BITS_PER_WORD, current.myValue);
					current = root;
				}
			}
			}
		}
		
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		int singleBit = in.readBits(1);
		if (singleBit == -1) {
			throw new HuffException("illegal tree header starts with "+singleBit);
		}
		if (singleBit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		} else {
			int valueBits =  in.readBits(BITS_PER_WORD+1);
			return new HuffNode(valueBits,0,null,null);
		}
	}
}