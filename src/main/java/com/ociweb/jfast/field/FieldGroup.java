package com.ociweb.jfast.field;

import com.ociweb.jfast.FASTAccept;
import com.ociweb.jfast.FASTProvide;
import com.ociweb.jfast.NullAdjuster;
import com.ociweb.jfast.ReadWriteEntry;
import com.ociweb.jfast.ValueDictionaryEntry;
import com.ociweb.jfast.primitive.PrimitiveReader;
import com.ociweb.jfast.primitive.PrimitiveWriter;
import com.ociweb.jfast.read.FieldTypeReadWrite;
import com.ociweb.jfast.read.PMapData;
import com.ociweb.jfast.read.ReadEntry;
import com.ociweb.jfast.write.WriteEntry;

public final class FieldGroup extends Field {

	
	//None of the operators apply to group but it does come in 2 forms group and sequence.
	
	private final int id;
	private final int pmapMaxSize;
	private final int repeat;
	private final Field[] fields;

	public FieldGroup(int id, Field[] fields, int pmapSize) {
		this.id = id;
		this.pmapMaxSize = pmapSize;
		this.fields = fields;
		this.repeat=1;
	}

	public final void reader(PrimitiveReader reader, FASTAccept visitor) {
				
		////////////////////////////////////
		//load the full pmap for this group
		////////////////////////////////////
		reader.readPMap(pmapMaxSize);
		////////
		//TODO: look for template id
		///////		
		
		/////////////////////
		//Process the fields
		/////////////////////
		int i = fields.length;
		while (--i>=0) {
			fields[i].reader(reader, visitor);
		}
		
		reader.popPMap();
		//end of reader
		
	}

	public void writer(PrimitiveWriter writer, FASTProvide provider) {
		
		writer.pushPMap(pmapMaxSize);
		
		int i = fields.length;
		while (--i>=0) {
			fields[i].writer(writer, provider);
		}
		
		writer.popPMap();
		//end of writer
		
	}
	
	public void reset() {
		
		//TODO: must walk matrix!
		
		//valueDictionaryEntry.reset();
		//end of reset
	}

}
