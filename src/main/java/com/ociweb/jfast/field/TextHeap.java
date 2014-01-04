package com.ociweb.jfast.field;

import java.io.IOException;

import com.ociweb.jfast.error.FASTException;

/**
 * Manage all the text (char sequences) for all the fields.
 * * Must maintain a minimum count of pointers to reduce GC overhead.
 * * Must be garbage free and not release objects/arrays unless reporting an error.
 * * Must work within the fixed size given or throw.
 * * Must support all the dynamic behavior needed by the operators.
 * * Must support constant time access to each block of text.
 * 
 * @author Nathan Tippy
 *
 */
public class TextHeap {

	private int totalContent = 0; //total chars consumed by current text.
	private int totalWorkspace = 0; //working space around each text.
	
	private final char[] data;
	private final int gapCount;
	private final int dataLength;
	
	private final int[] initTat;
	private final char[] initBuffer;
	
	//remain true unless memory gets low and it has to give up any margin
	private boolean preserveWorkspace = true;
	private final int textItemCount;
	
	//text allocation table
	private final int[] tat;
	//4 ints per text body.
	//start index position (inclusive)
	//stop index limit (exclusive)
	//max prefix append
	//max postfix append
	
	
	TextHeap(int singleTextSize, int singleGapSize, int fixedTextItemCount) {
		this(singleTextSize,singleGapSize,fixedTextItemCount,0,new int[0],new char[0][]);
	}
	
	
	TextHeap(int singleTextSize, int singleGapSize, int fixedTextItemCount, 
			int charInitTotalLength, int[] charInitIndex, char[][] charInitValue) {
		
		textItemCount = fixedTextItemCount;
		
		gapCount = fixedTextItemCount+1;
		dataLength = (singleGapSize*gapCount)+(singleTextSize*fixedTextItemCount);
		data = new char[dataLength];
		tat = new int[fixedTextItemCount<<2];
		initTat = new int[fixedTextItemCount<<1];
		
		
		int i = tat.length;
		int j = dataLength+(singleTextSize>>1);
		while (--i>=0) {
			int idx = (j-=(singleTextSize+singleGapSize));
			i--;
			i--;
			tat[i--]=idx;
			tat[i]=idx;		
		}	
		

		if (null==charInitValue || charInitValue.length==0) {
			initBuffer = null;
		} else {
			initBuffer= new char[charInitTotalLength];
			
			int stopIdx = charInitTotalLength;
			int startIdx = stopIdx;
			
			i = charInitValue.length;
			while (--i>=0) {
				int len = null==charInitValue[i]?0:charInitValue[i].length;
				
				
				startIdx -= len;	
				if (len>0) {
					System.arraycopy(charInitValue[i], 0, initBuffer, startIdx, len);
				}
				//will be zero zero for values without constants.
				int offset = i<<1;
				initTat[offset] = startIdx;
				initTat[offset+1] = stopIdx;
										
				stopIdx = startIdx;
			}	
		}
	}
	

	public void reset() {
	
		int i = textItemCount;
		while (--i>=0) {
			int b = i<<1;
						
			if (initTat[b]==initTat[b+1]) {
				setNull(i);				
			} else {
				set(i, initBuffer, initTat[b], initTat[b+1]-initTat[b]);
			}			
		}
	}
	
	
	//Caution: this method will create a new String instance
	public CharSequence getSub(int idx, int start, int end) {
		int offset = idx<<2;
		int a = tat[offset]+start;
		int b = Math.min(tat[offset+1], tat[offset]+end );
		System.err.println(a+","+b+","+data.length);
		return new String(data,
				Math.max(0,tat[offset]+start),
				Math.max(0, Math.min(tat[offset+1], tat[offset]+end )));
	}
	
	void setZeroLength(int idx) {
		int offset = idx<<2;
		tat[offset+1] = tat[offset];
	}
	
	void setNull(int idx) {
		int offset = idx<<2;
		tat[offset+1] = tat[offset]-1;
	}
	
	public boolean isNull(int idx) {
		int offset = idx<<2;
		return tat[offset+1] == tat[offset]-1;
	}


	char[] rawAccess() {
		return data;
	}
	
	int allocate(int idx, int sourceLen) {
		
		int offset = idx<<2;
		
		int target = makeRoom(offset, sourceLen);

		int limit = target+sourceLen;
		if (limit>dataLength) {
			target = dataLength-sourceLen;
		}
		
		//full replace
		totalContent+=(sourceLen-(tat[offset+1]-tat[offset]));
		tat[offset] = target;
		tat[offset+1] = limit;
		
		return target;
	}


	private int makeRoom(int offset, int sourceLen) {
		int prevTextStop  = offset   == 0           ? 0           : tat[offset-3];
		int nextTextStart = offset+4 >= tat.length  ? dataLength  : tat[offset+4];
		int space = nextTextStart - prevTextStop;
		int middleIdx = (space-sourceLen)>>1;
		
		if (sourceLen>space) {
			//we do not have enough space move to the bigger side.
			makeRoom(offset, middleIdx>(dataLength>>1), (sourceLen + tat[offset+2] + tat[offset+3])-space);
    		prevTextStop  = offset   == 0           ? 0           : tat[offset-3];
    		nextTextStart = offset+4 >= dataLength ? dataLength : tat[offset+4]; 		
    		middleIdx = ((nextTextStart-prevTextStop)-sourceLen)>>1;         
		}
		
		int target = prevTextStop+middleIdx;
		return target<0 ? 0 : target;
	}
		

	void set(int idx, char[] source, int startFrom, int copyLength) {
		int offset = idx<<2;
		
		totalContent+=(copyLength-(tat[offset+1]-tat[offset]));
		int target = makeRoom(offset, copyLength);
		tat[offset] = target;
		tat[offset+1] = target+copyLength;
		
		System.arraycopy(source, startFrom, data, target, copyLength);
	}
	
	void set(int idx, CharSequence source, int startFrom, int copyLength) {
		assert(startFrom<=source.length());
		assert(copyLength-startFrom<source.length());
		assert(startFrom>=0);
		assert(copyLength>=0);
		
		int offset = idx<<2;
		
		totalContent+=(copyLength-(tat[offset+1]-tat[offset]));
		int target = makeRoom(offset, copyLength);
		tat[offset] = target;
		tat[offset+1] = target+copyLength;
		
		int srcLimit = copyLength+startFrom;
		while (startFrom<srcLimit) {
			data[target++] = source.charAt(startFrom++);
		}
	}
	
	private void makeRoom(int offsetNeedingRoom, boolean startBefore, int totalDesired) {

		if (startBefore) {
			totalDesired = makeRoomBeforeFirst(offsetNeedingRoom, totalDesired);			
		} else {
			totalDesired = makeRoomAfterFirst(offsetNeedingRoom, totalDesired);
		}
		if (totalDesired>0) {
			throw new RuntimeException("TextHeap must be initialized with more ram for required text");
		}
	}

	private int makeRoomAfterFirst(int offsetNeedingRoom, int totalDesired) {
		//compress rear its larger
		totalDesired = compressAfter(offsetNeedingRoom+4, totalDesired, preserveWorkspace);
		if (totalDesired>0) {
			totalDesired = compressBefore(offsetNeedingRoom-4, totalDesired, preserveWorkspace);
			if (totalDesired>0) {
				preserveWorkspace = false;
				totalDesired = compressAfter(offsetNeedingRoom+4, totalDesired, preserveWorkspace);
				if (totalDesired>0) {
					totalDesired = compressBefore(offsetNeedingRoom-4, totalDesired, preserveWorkspace);
				}
			}
		}
		return totalDesired;
	}

	private int makeRoomBeforeFirst(int offsetNeedingRoom, int totalDesired) {
		//compress front its larger
		totalDesired = compressBefore(offsetNeedingRoom-4, totalDesired, preserveWorkspace);
		if (totalDesired>0) {
			totalDesired = compressAfter(offsetNeedingRoom+4, totalDesired, preserveWorkspace);
			if (totalDesired>0) {
				preserveWorkspace = false;
				totalDesired = compressBefore(offsetNeedingRoom-4, totalDesired, preserveWorkspace);
				if (totalDesired>0) {
					totalDesired = compressAfter(offsetNeedingRoom+4, totalDesired, preserveWorkspace);
				}
			}
		}
		return totalDesired;
	}

	
	private int compressBefore(int stopOffset, int totalDesired, boolean preserveWorkspace) {
		//moving everything to the left until totalDesired
		//start at zero and move everything needed if possible.
		int dataIdx = 0;
		int offset = 0;
		while (offset<=stopOffset) {
			
			int leftBound = 0;
			int rightBound = 0;
			int textLength = tat[offset+1] - tat[offset]; 
			int totalNeed = textLength;
			
			if (preserveWorkspace) {
				int padding = 0;
				leftBound = tat[offset+2]; 
				rightBound = tat[offset+3];
				
				padding += leftBound;
				padding += tat[offset+3];
				int avgPadding = (dataLength - (this.totalContent+this.totalWorkspace))/gapCount;
				if (avgPadding<0) {
					avgPadding = 0;
				}
				
				padding += avgPadding;
				
				int halfPad = avgPadding>>1;
				leftBound += halfPad;
				rightBound += halfPad;
				
				totalNeed += padding;
			}
					
			int copyTo = dataIdx+leftBound;
			
			if (copyTo<tat[offset]) {
				//copy down and save some room.
				System.arraycopy(data, tat[offset], data, copyTo, textLength);
				tat[offset] = copyTo;
				int oldStop = tat[offset+1];
				int newStop = copyTo+textLength;
				tat[offset+1] = newStop;
				
				totalDesired -= (oldStop-newStop);
				
				dataIdx = dataIdx+totalNeed;
			} else {
				//it is already lower than this point.
				dataIdx = tat[offset+1]+rightBound;
			}
			offset+=4;
		}
	
		return totalDesired;
	}

	private int compressAfter(int stopOffset, int totalDesired, boolean preserveWorkspace) {
		//moving everything to the right until totalDesired
		//start at zero and move everything needed if possible.
		int dataIdx = dataLength;
		int offset = tat.length-4;
		while (offset>=stopOffset) {
			
			int leftBound = 0;
			int rightBound = 0;
			int textLength = tat[offset+1] - tat[offset]; 
			int totalNeed = textLength;
			
			if (preserveWorkspace) {
				int padding = 0;
				leftBound = tat[offset+2]; 
				rightBound = tat[offset+3];
				
				padding += leftBound;
				padding += tat[offset+3];
				int avgPadding = (dataLength - (this.totalContent+this.totalWorkspace))/gapCount;
				if (avgPadding<0) {
					avgPadding = 0;
				}
				
				padding += avgPadding;
				
				int halfPad = avgPadding>>1;
				leftBound += halfPad;
				rightBound += halfPad;
				
				totalNeed += padding;
			}
					
			int newStart = dataIdx-(rightBound+textLength);
			StringBuilder builder = new StringBuilder();
			inspectHeap(builder);
			//System.err.println("before:"+builder.toString());
			if (newStart>tat[offset]) {
				//copy up and save some room.
				System.arraycopy(data, tat[offset], data, newStart, textLength);
				int oldStart = tat[offset];
				tat[offset] = newStart;
				totalDesired -= (newStart-oldStart);
				tat[offset+1] = newStart+textLength;
				dataIdx = dataIdx - totalNeed;
			} else {
				//it is already greater than this point.
				dataIdx = tat[offset]-leftBound;
			}
			
			builder.setLength(0);
			inspectHeap(builder);
			//System.err.println("after :"+builder.toString());
			
			offset-=4;
		}
	
		return totalDesired;
	}

	private void inspectHeap(StringBuilder target) {
		target.append('[');
		int i=0;
		while (i<dataLength) {
			if (data[i]==0) {
				target.append('_');
			} else {
				target.append(data[i]);
			}
			i++;			
		}
		target.append(']');
		
	}


	public void appendTail(int idx, int trimTail, int startFrom, CharSequence value) {
		//if not room make room checking after first because thats where we want to copy the tail.

		int sourceLen = value.length()-startFrom;
		int targetPos = makeSpaceForAppend(trimTail, idx, sourceLen);
					
		int i = value.length();
		int j = targetPos+sourceLen;
		while (--i>=startFrom) {
			data[--j] = value.charAt(i);
		}
	}

	//append chars on to the end of the text after applying trim
	//may need to move existing text or following texts
	//if there is no room after moving everything throws
	void appendTail(int idx, int trimTail, char[] source, int sourceIdx, int sourceLen) {
		//if not room make room checking after first because thats where we want to copy the tail.
		System.arraycopy(source, sourceIdx, data, makeSpaceForAppend(trimTail, idx, sourceLen), sourceLen);
	}
		
	void appendTail(int idx, char value) {
		//if not room make room checking after first because thats where we want to copy the tail.
		int offset = idx<<2;
		makeSpaceForAppend(offset, 1);
		//everything is now ready to trim and copy.
		data[tat[offset+1]++] = value;
	}
	
	void trimTail(int idx, int trim) {
		int offset = idx<<2;
		
		tat[offset+1] -= trim;
		
	}
	
	void trimHead(int idx, int trim) {
		int offset = idx<<2;
		
		int tmp = tat[offset] + trim;
		int stp = tat[offset+1];
		if (tmp > stp) {
			tmp = stp;
		}
		tat[offset] = tmp;
		
	}
	
	int makeSpaceForAppend(int trimTail, int idx, int sourceLen) {
		int textLen = (sourceLen-trimTail);
		
		int offset = idx<<2;
		
		makeSpaceForAppend(offset, textLen);
		//everything is now ready to trim and copy.
		
		//keep the max head append size
		int maxTail = tat[offset+3];
		int dif=(sourceLen-trimTail);
		if (dif>maxTail) {
			tat[offset+3] = dif;
		}
		//target position
		int targetPos = tat[offset+1]-trimTail;
		tat[offset+1] = targetPos+sourceLen;
		return targetPos;
	}

	private void makeSpaceForAppend(int offset, int textLen) {
		if (tat[offset]>tat[offset+1]) {
			//switch from null to zero length
			tat[offset+1]=tat[offset];
		}
		
		int stop = tat[offset+1]+textLen;
		int limit = offset+4<tat.length ? tat[offset+4] : dataLength;
		
		if (stop>=limit) {
			int floor = offset-3>=0 ? tat[offset-3] : 0;
			int space = limit- floor;
			int need = stop - tat[offset];
			
			if (need>space) {
				makeRoom(offset, false, need);			
			}
			//we have some space so just shift the existing data.
			int len = tat[offset+1]-tat[offset];
			System.arraycopy(data, tat[offset], data, floor, len);
			tat[offset] = floor;
			tat[offset+1] = floor+len;

		}
	}
	
	//append chars on to the front of the text after applying trim
	//may need to move existing text or previous texts
	//if there is no room after moving everything throws
	void appendHead(int idx, int trimHead, char[] source, int sourceIdx, int sourceLen) {
		System.arraycopy(source, sourceIdx, data, makeSpaceForPrepend(idx, trimHead, sourceLen), sourceLen);			
	}
	
	void appendHead(int idx, int trimHead, CharSequence value) {
		int sourceLen = value.length()-trimHead;
		int newStart = makeSpaceForPrepend(idx, trimHead, sourceLen);
				
		int i = sourceLen;
		int j = newStart;
		while (--i>=0) {
			data[j] = value.charAt(i);
		}
						
	}
	
	void appendHead(int idx, char value) {
		//if not room make room checking after first because thats where we want to copy the tail.
		int offset = idx<<2;
		makeSpaceForPrepend(offset, 1);
		//everything is now ready to trim and copy.
		data[tat[offset+1]++] = value;
	}

	int makeSpaceForPrepend(int idx, int trimHead, int sourceLen) {
		int textLength = sourceLen-trimHead;
		
		//if not room make room checking before first because thats where we want to copy the head.
		int offset = idx<<2;
				
		makeSpaceForPrepend(offset, textLength);
		//everything is now ready to trim and copy.
		
		//keep the max head append size
		int maxHead = tat[offset+2];
		if (textLength>maxHead) {
			tat[offset+2] = textLength;
		}
			
		//everything is now ready to trim and copy.
		int newStart = tat[offset]-textLength;
		tat[offset]=newStart;
		return newStart;
	}


	private void makeSpaceForPrepend(int offset, int textLength) {
		if (tat[offset]>tat[offset+1]) {
			//null or empty string detected so change to simple set
			tat[offset+1]=tat[offset];
		}
		
		int start = tat[offset]-textLength;
		int limit = offset-3<0 ? 0 : tat[offset-3];
				
		if (start<limit) {
			int stop = offset+4<tat.length ? tat[offset+4] : dataLength;
			int space = stop - limit;
			int need = tat[offset+1]-start;
			
			if (need>space) {
				makeRoom(offset, true, need);			
			}
			//we have some space so just shift the existing data.
			int len = tat[offset+1] - tat[offset];
			System.arraycopy(data, tat[offset], data, start, len);
			tat[offset] = start;
			tat[offset+1] = start+len;
			
		}
	}
	
	
	///////////
	//////////
	//read access methods
	//////////
	//////////
	

	public char getChar(int idx, int index) {
		return data[tat[idx<<2]+index];
	}
	
	public int get(int idx, char[] target, int targetIdx) {
		if (idx<0) {
			int offset = idx << 1; //this shift left also removes the top bit! sweet.
			
			int pos = initTat[offset];
			int len = initTat[offset+1]-pos;
			System.arraycopy(initBuffer, pos, target, targetIdx, len);
			return len;
			
		} else {
		
			int offset = idx<<2;
			
			int pos = tat[offset];
			int len = tat[offset+1]-pos;
			
			System.arraycopy(data, pos, target, targetIdx, len);
			return len;
		}
	}
	
	public void get(int idx, Appendable target) {
		if (idx<0) {
			int offset = idx << 1; //this shift left also removes the top bit! sweet.
			
			int pos = initTat[offset];
			int lim = initTat[offset+1];
			
			try {
				while (pos<lim) {
						target.append(initBuffer[pos++]);
				}
			} catch (IOException e) {
				throw new FASTException(e);
			}
			
		} else {
			int offset = idx<<2;
			
			int pos = tat[offset];
			int lim = tat[offset+1];
			
			try {
				while (pos<lim) {
						target.append(data[pos++]);
				}
			} catch (IOException e) {
				throw new FASTException(e);
			}
		}
	}
	
	public int countHeadMatch(int idx, CharSequence value) {
		int offset = idx<<2;
		
		int pos = tat[offset];
		int lim = tat[offset+1];
		
		int i = 0;
		int limit = Math.min(value.length(),lim-pos);
		while (i<limit && data[pos+i]==value.charAt(i)) {
			i++;
		}
		return i;
	}
	
	public int countTailMatch(int idx, CharSequence value) {
		int offset = idx<<2;
		
		int pos = tat[offset];
		int lim = tat[offset+1];
		int vlim = value.length();
		
		int limit = Math.min(vlim,lim-pos);
		int i = 1;
		while (i<=limit && data[lim-i]==value.charAt(vlim-i)) {
			i++;
		}
		return i-1;
	}
	

	public boolean equals(int idx, CharSequence value) {
		int offset = idx<<2;
		
		int pos = tat[offset];
		int lim = tat[offset+1];
		
		int i = value.length();
		if (lim-pos!=i) {
			return false;
		}
		while (--i>=0) {
			if (value.charAt(i)!=data[pos+i]) {
				return false;
			}
		}
		return true;
	}
	
	public int countHeadMatch(int idx, char[] source, int sourceIdx, int sourceLength) {
		int offset = idx<<2;
		
		int pos = tat[offset];
		int lim = tat[offset+1];
		
		int i = 0;
		int limit = Math.min(sourceLength,lim-pos);
		while (i<limit && data[pos+i]==source[sourceIdx+i]) {
			i++;
		}
		return i;
	}
	
	public int countTailMatch(int idx, char[] source, int sourceIdx, int sourceLength) {
		int offset = idx<<2;
		
		int pos = tat[offset];
		int lim = tat[offset+1];
		int vlim = sourceLength;
		
		int limit = Math.min(vlim,lim-pos);
		int i = 1;
		int last = sourceLength+sourceIdx;
		while (i<=limit && data[lim-i]==source[last-i]) {
			i++;
		}
		return i-1;
	}
	
	public boolean equals(int idx, char[] source, int sourceIdx, int sourceLength) {
		int offset = idx<<2;
		
		int pos = tat[offset];
		int lim = tat[offset+1];
		
		int i = sourceLength;
		if (lim-pos!=i) {
			return false;
		}
		int j = pos+i;
		while (--i>=0) {
			if (source[sourceIdx+i]!=data[--j]) {
				return false;
			}
		}
		return true;
	}

	public int textCount() {
		return tat.length>>2;
	}
	
	public int length(int idx) {
		if (idx<0) {
			int offset = idx << 1; //this shift left also removes the top bit! sweet.
			return initTat[offset+1] - initTat[offset];
		} else {
			int offset = idx<<2;
			return tat[offset+1] - tat[offset];
		}
	}

}
