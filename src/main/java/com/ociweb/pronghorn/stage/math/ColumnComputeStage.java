package com.ociweb.pronghorn.stage.math;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.math.BuildMatrixCompute.MatrixTypes;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class ColumnComputeStage<M extends MatrixSchema, C extends MatrixSchema, R extends MatrixSchema > extends PronghornStage {

	private Logger logger = LoggerFactory.getLogger(ColumnComputeStage.class);
	
	private Pipe<ColumnSchema<C>>[] colInput;
	private Pipe<R> matrixInput;
	private Pipe<ColumnSchema<M>> colOutput;
	private M resultSchema;
	private R rSchema;
	private MatrixTypes type;
	
	protected ColumnComputeStage(GraphManager graphManager, Pipe<ColumnSchema<C>>[] colInput,  Pipe<R> matrixInput, Pipe<ColumnSchema<M>> colOutput,
			                     M matrixSchema, C cSchema, R rSchema) {
		super(graphManager, join(colInput,matrixInput), colOutput);
		this.colInput = colInput;
		this.matrixInput = matrixInput;
		this.colOutput= colOutput;
		this.resultSchema = matrixSchema;
		this.rSchema = rSchema;
		this.type = rSchema.type;
		
		if (cSchema.getColumns() != rSchema.getRows()) {
			throw new UnsupportedOperationException("column count of left input must match row count of right input "+cSchema.getColumns()+" vs "+rSchema.getRows());
		}
		
		if (resultSchema.getColumns() != rSchema.getColumns()) {
			throw new UnsupportedOperationException("column count of right input must match result output "+rSchema.getColumns()+" vs "+resultSchema.getColumns());
		}
			
        if (resultSchema.getRows() != cSchema.getRows()) {
        	throw new UnsupportedOperationException("rows count of left input must match result output "+cSchema.getColumns()+" vs "+resultSchema.getRows());
		}
		        
		if (cSchema.type != matrixSchema.type) {
			throw new UnsupportedOperationException("type mismatch");
		}
		
		if (rSchema.type != matrixSchema.type) {
			throw new UnsupportedOperationException("type mismatch");
		}
		
        
	}

	@Override
	public void run() {
		
		//logger.info("{} {} {} {} ",Pipe.hasContentToRead(this.matrixInput),  Pipe.hasContentToRead(this.colInput),  Pipe.hasRoomForWrite(this.colOutput), this.colOutput);
		
		int activeCol = 0;//TODO: add suport for multiple cols in loop.
		
		while (Pipe.hasContentToRead(this.matrixInput) && 
			   Pipe.hasContentToRead(this.colInput[activeCol]) &&
			   Pipe.hasRoomForWrite(this.colOutput)) {

			
			int colId = Pipe.takeMsgIdx(this.colInput[activeCol]);			
			int matrixId = Pipe.takeMsgIdx(this.matrixInput);
			
			if (colId>=0 && matrixId>=0) {
				assert(matrixId == resultSchema.matrixId);
				int msgOutSize = Pipe.addMsgIdx(this.colOutput, resultSchema.columnId);
				
				final long colStartPos = ((int)Pipe.getWorkingTailPosition(this.colInput[activeCol]));
								
				//Due to strong typing we know how long the columns are and how big every matrix is

				//process this once for each row in the exported matrix
				int k = this.resultSchema.getRows();
				while (--k>=0) {	
										
					////////////////
					//apply this column to the full matrix row
					//produce a single value for the output column
					////////////////
					
				    Pipe.setWorkingTailPosition(this.colInput[activeCol], colStartPos);//reset to the beginning of column.	
				    type.computeColumn(this.rSchema.getRows(), colInput[activeCol], matrixInput, colOutput);
   				    
				}				
	            //we have now finished 1 column of the result.
				
								
				//always confirm writes before reads.
				Pipe.confirmLowLevelWrite(this.colOutput, msgOutSize);
				Pipe.publishWrites(this.colOutput);
			    
				Pipe.confirmLowLevelRead(this.colInput[activeCol], Pipe.sizeOf(this.colInput[activeCol], colId)); //TODO: make constants.
				Pipe.confirmLowLevelRead(this.matrixInput, Pipe.sizeOf(this.matrixInput, matrixId));
				
				Pipe.releaseReadLock(this.colInput[activeCol]);
				Pipe.releaseReadLock(this.matrixInput);
				
				
				
			} else {
				//we already know that colOuput has room for write
				Pipe.publishEOF(this.colOutput);				
				requestShutdown();
				return;
			}
			
			
		}
		
		
		
	}

}
