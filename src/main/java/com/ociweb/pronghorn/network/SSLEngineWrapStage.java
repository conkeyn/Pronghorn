package com.ociweb.pronghorn.network;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.network.schema.ClientNetRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class SSLEngineWrapStage extends PronghornStage {

	private final ClientConnectionManager        ccm;
	private final Pipe<ClientNetRequestSchema>[] encryptedContent; 
	private final Pipe<ClientNetRequestSchema>[] plainContent;
	private ByteBuffer[]                         secureBuffers;
	private Logger                               logger = LoggerFactory.getLogger(SSLEngineWrapStage.class);
	
	private long          totalNS;
	private int           calls;
	private final boolean isServer;
	private int           shutdownCount;
	private final int     SIZE_HANDSHAKE_AND_DISCONNECT;
	
	protected SSLEngineWrapStage(GraphManager graphManager, ClientConnectionManager ccm,
            Pipe<ClientNetRequestSchema>[] plainContent, Pipe<ClientNetRequestSchema>[] encryptedContent) {
		this(graphManager,ccm,false,plainContent,encryptedContent);
	}
	
	protected SSLEngineWrapStage(GraphManager graphManager, ClientConnectionManager ccm, boolean isServer,
			                     Pipe<ClientNetRequestSchema>[] plainContent, Pipe<ClientNetRequestSchema>[] encryptedContent) {
		
		super(graphManager, plainContent, encryptedContent);

		shutdownCount = plainContent.length;
		SIZE_HANDSHAKE_AND_DISCONNECT = Pipe.sizeOf(encryptedContent[0], ClientNetRequestSchema.MSG_SIMPLEDISCONNECT_101)
				+Pipe.sizeOf(encryptedContent[0], ClientNetRequestSchema.MSG_SIMPLEDISCONNECT_101);
		
		this.ccm = ccm;
		this.encryptedContent = encryptedContent;
		this.plainContent = plainContent;
		this.isServer = isServer;
		assert(encryptedContent.length==plainContent.length);
		
	}

	@Override
	public void startup() {
		
		//must allocate buffers for the out of order content 
		int c = plainContent.length;
		secureBuffers = new ByteBuffer[c];
		while (--c>=0) {
			secureBuffers[c] = ByteBuffer.allocate(plainContent[c].maxAvgVarLen*2);
		}				
		
	}
	
	@Override
	public void run() {
		long start = System.nanoTime();
		calls++;
		
		int i = encryptedContent.length;
		while (--i >= 0) {
						
			ClientConnection.engineWrap(ccm, plainContent[i], encryptedContent[i], secureBuffers[i], isServer);			
			
			/////////////////////////////////////
			//close the connection logic
			//if connection is open we must finish the handshake.
			////////////////////////////////////
			if (PipeWriter.hasRoomForFragmentOfSize(encryptedContent[i], SIZE_HANDSHAKE_AND_DISCONNECT)
				&& PipeReader.peekMsg(plainContent[i], ClientNetRequestSchema.MSG_SIMPLEDISCONNECT_101)) {
				PipeReader.tryReadFragment(plainContent[i]);
				long connectionId = PipeReader.readLong(plainContent[i], ClientNetRequestSchema.MSG_SIMPLEDISCONNECT_101_FIELD_CONNECTIONID_101);
				
				ClientConnection connection = ccm.get(connectionId);
				if (null!=connection) {
					assert(connection.isDisconnecting()) : "should only receive disconnect messages on connections which are disconnecting.";
					ClientConnection.handShakeWrapIfNeeded(connection, encryptedContent[i], secureBuffers[i]);					
				}				
				
				PipeWriter.tryWriteFragment(encryptedContent[i], ClientNetRequestSchema.MSG_SIMPLEDISCONNECT_101);
				PipeWriter.writeLong(encryptedContent[i], ClientNetRequestSchema.MSG_SIMPLEDISCONNECT_101_FIELD_CONNECTIONID_101, connectionId);
				PipeWriter.publishWrites(encryptedContent[i]);
				
				PipeReader.releaseReadLock(plainContent[i]);
			}			
			
			///////////////////////////
			//shutdown this stage logic
			///////////////////////////
			if (PipeReader.peekMsg(plainContent[i], -1)) {
				PipeReader.tryReadFragment(plainContent[i]);
				PipeReader.releaseReadLock(plainContent[i]);
				if (--shutdownCount<=0) {
					requestShutdown();
					break;
				}
			}
			
		}
		
		totalNS += System.nanoTime()-start;
		
	}

    @Override
    public void shutdown() {
    	
    	int j = encryptedContent.length;
    	while (--j>=0) {
    		PipeWriter.publishEOF(encryptedContent[j]);
    	}    	
    	
    	boolean debug=false;
    	
    	if (debug) {    	
	    	long totalBytesOfContent = 0;
	    	int i = plainContent.length;
	    	while (--i>=0) {
	    		totalBytesOfContent += Pipe.getBlobRingTailPosition(plainContent[i]);
	    	}
	    	
	
			float mbps = (float) ( (8_000d*totalBytesOfContent)/ (double)totalNS);
	    	logger.info("wrapped total bytes "+totalBytesOfContent+"    "+mbps+"mbps");
	    	logger.info("wrapped total time "+totalNS+"ns total callls "+calls);
    	}
    	
    }
	
}
