package com.ociweb.pronghorn.network.http;

import com.ociweb.pronghorn.network.ClientConnection;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;

public class HTTPClientUtil {

	public static void cleanCloseConnection(ClientConnection connectionToKill, Pipe<NetPayloadSchema> pipe) {
		
		//do not close that will be done by last stage
		//must be done first before we send the message
		connectionToKill.beginDisconnect();
	
		Pipe.presumeRoomForWrite(pipe);
		int size = Pipe.addMsgIdx(pipe, NetPayloadSchema.MSG_DISCONNECT_203);
		Pipe.addLongValue(connectionToKill.getId(), pipe);//   NetPayloadSchema.MSG_DISCONNECT_203_FIELD_CONNECTIONID_201, connectionToKill.getId());
		Pipe.confirmLowLevelWrite(pipe, size);
		Pipe.publishWrites(pipe);
		
	}

	public static void processGetLogic(long now, Pipe<ClientHTTPRequestSchema> requestPipe, ClientConnection clientConnection,
			Pipe<NetPayloadSchema> outputPipe) {
		
		clientConnection.setLastUsedTime(now);		        	
		//logger.info("sent get request down pipe {} ",outIdx);
		
		clientConnection.incRequestsSent();//count of messages can only be done here.
	
		Pipe.presumeRoomForWrite(outputPipe);
		
		   	int pSize = Pipe.addMsgIdx(outputPipe, NetPayloadSchema.MSG_PLAIN_210);
		   	
		   	Pipe.addLongValue(clientConnection.id, outputPipe); //, NetPayloadSchema.MSG_PLAIN_210_FIELD_CONNECTIONID_201, clientConnection.id);
		   	Pipe.addLongValue(System.currentTimeMillis(), outputPipe);
		   	Pipe.addLongValue(0, outputPipe); // NetPayloadSchema.MSG_PLAIN_210_FIELD_POSITION_206, 0);
		 	
		 	
			DataOutputBlobWriter<NetPayloadSchema> activeWriter = Pipe.outputStream(outputPipe);
			DataOutputBlobWriter.openField(activeWriter);
			
			DataOutputBlobWriter.encodeAsUTF8(activeWriter,"GET");
			
			int userId = Pipe.takeInt(requestPipe);
			int port   = Pipe.takeInt(requestPipe);
			int hostMeta = Pipe.takeRingByteMetaData(requestPipe);
			int hostLen  = Pipe.takeRingByteLen(requestPipe);
			int hostPos = Pipe.bytePosition(hostMeta, requestPipe, hostLen);
			
		  	int meta = Pipe.takeRingByteMetaData(requestPipe); //ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3
			int len  = Pipe.takeRingByteLen(requestPipe);
		    int first = Pipe.bytePosition(meta, requestPipe, len);					                	
		
			int headersMeta = Pipe.takeRingByteMetaData(requestPipe); // HEADER
			int headersLen  = Pipe.takeRingByteMetaData(requestPipe);
			int headersPos  = Pipe.bytePosition(headersMeta, requestPipe, headersLen);
		    
		    boolean prePendSlash = (0==len) || ('/' != Pipe.byteBackingArray(meta, requestPipe)[first&Pipe.blobMask(requestPipe)]);
	
			if (prePendSlash) { //NOTE: these can be pre-coverted to bytes so we need not convert on each write. may want to improve.
				DataOutputBlobWriter.encodeAsUTF8(activeWriter," /");
			} else {
				DataOutputBlobWriter.encodeAsUTF8(activeWriter," ");
			}
			
			//Reading from UTF8 field and writing to UTF8 encoded field so we are doing a direct copy here.
			Pipe.readBytes(requestPipe, activeWriter, meta, len);//, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, activeWriter);
	
			final byte[] hostBack = Pipe.byteBackingArray(hostMeta, requestPipe);//, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
			final int hostMask    = Pipe.blobMask(requestPipe);	
			
			
			HeaderUtil.writeHeaderBeginning(hostBack, hostPos, hostLen, hostMask, activeWriter);
			
			HeaderUtil.writeHeaderMiddle(activeWriter, HTTPClientRequestStage.implementationVersion);
			activeWriter.write(Pipe.byteBackingArray(headersMeta, requestPipe), headersPos, headersLen, Pipe.blobMask(requestPipe));
									
			HeaderUtil.writeHeaderEnding(activeWriter, true, (long) 0);
		
							                	
			DataOutputBlobWriter.closeLowLevelField(activeWriter);//, NetPayloadSchema.MSG_PLAIN_210_FIELD_PAYLOAD_204);
	
			//System.err.println("SENT: \n"+new String(activeWriter.toByteArray())+"\n");
			
			Pipe.confirmLowLevelWrite(outputPipe,pSize);
			Pipe.publishWrites(outputPipe);
	}

	public static void processPostLogic(long now, Pipe<ClientHTTPRequestSchema> requestPipe, ClientConnection clientConnection,
			Pipe<NetPayloadSchema> outputPipe) {
		clientConnection.setLastUsedTime(now);
		clientConnection.incRequestsSent();//count of messages can only be done here.
	
		Pipe.presumeRoomForWrite(outputPipe);
			
			int pSize = Pipe.addMsgIdx(outputPipe, NetPayloadSchema.MSG_PLAIN_210);
			
			Pipe.addLongValue(clientConnection.id, outputPipe); //NetPayloadSchema.MSG_PLAIN_210_FIELD_CONNECTIONID_201, clientConnection.id);
			Pipe.addLongValue(System.currentTimeMillis(), outputPipe);
			Pipe.addLongValue(0, outputPipe); // NetPayloadSchema.MSG_PLAIN_210_FIELD_POSITION_206, 0);
			
			DataOutputBlobWriter<NetPayloadSchema> activeWriter = Pipe.outputStream(outputPipe);
			DataOutputBlobWriter.openField(activeWriter);
					                
			DataOutputBlobWriter.encodeAsUTF8(activeWriter,"POST");
			
			int userId = Pipe.takeInt(requestPipe);
			int port   = Pipe.takeInt(requestPipe);
			int hostMeta = Pipe.takeRingByteMetaData(requestPipe);
			int hostLen  = Pipe.takeRingByteLen(requestPipe);
			int hostPos = Pipe.bytePosition(hostMeta, requestPipe, hostLen);
			
		  	int meta = Pipe.takeRingByteMetaData(requestPipe); //ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3
			int len  = Pipe.takeRingByteLen(requestPipe);
		    int first = Pipe.bytePosition(meta, requestPipe, len);					                	
		
		    boolean prePendSlash = (0==len) || ('/' != Pipe.byteBackingArray(meta, requestPipe)[first&Pipe.blobMask(requestPipe)]);
	
			if (prePendSlash) { //NOTE: these can be pre-coverted to bytes so we need not convert on each write. may want to improve.
				DataOutputBlobWriter.encodeAsUTF8(activeWriter," /");
			} else {
				DataOutputBlobWriter.encodeAsUTF8(activeWriter," ");
			}
			
			//Reading from UTF8 field and writing to UTF8 encoded field so we are doing a direct copy here.
			Pipe.readBytes(requestPipe, activeWriter, meta, len);//, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, activeWriter);
			
			int headersMeta = Pipe.takeRingByteMetaData(requestPipe); // HEADER 7
			int headersLen  = Pipe.takeRingByteMetaData(requestPipe);
			int headersPos  = Pipe.bytePosition(headersMeta, requestPipe, headersLen);
			
			
			int payloadMeta = Pipe.takeRingByteMetaData(requestPipe); //MSG_HTTPPOST_101_FIELD_PAYLOAD_5
			int payloadLen  = Pipe.takeRingByteMetaData(requestPipe);
			
			
			//For chunked must pass in -1
	
			//TODO: this field can no be any loger than 4G so we cant post anything larger than that
			//TODO: we also need support for chunking which will need multiple mesage fragments
			//TODO: need new message type for chunking/streaming post
			
			final byte[] hostBack = Pipe.byteBackingArray(hostMeta, requestPipe);//, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
			final int backingMask    = Pipe.blobMask(requestPipe);	
			
			HeaderUtil.writeHeaderBeginning(hostBack, hostPos, hostLen, backingMask, activeWriter);
			
			HeaderUtil.writeHeaderMiddle(activeWriter, HTTPClientRequestStage.implementationVersion);
			//callers custom headers are written where.
			activeWriter.write(Pipe.byteBackingArray(headersMeta, requestPipe), headersPos, headersLen, backingMask);	
			boolean keepOpen = true;
			HeaderUtil.writeHeaderEnding(activeWriter, keepOpen, (long) payloadLen);
			
			Pipe.readBytes(requestPipe, activeWriter, payloadMeta, payloadLen); //MSG_HTTPPOST_101_FIELD_PAYLOAD_5
			
			int postLen = DataOutputBlobWriter.closeLowLevelField(activeWriter);//, NetPayloadSchema.MSG_PLAIN_210_FIELD_PAYLOAD_204);
				
			
			//System.err.println("SENT: \n"+new String(activeWriter.toByteArray())+"\n");
			
			
			Pipe.confirmLowLevelWrite(outputPipe,pSize);
			Pipe.publishWrites(outputPipe);
	
	}

	public static void publishGet(Pipe<ClientHTTPRequestSchema> requestPipe, ClientConnection clientConnection,
			Pipe<NetPayloadSchema> outputPipe, long now) {
		
		clientConnection.incRequestsSent();//count of messages can only be done here, AFTER requestPipeLineIdx
		
		//long pos = Pipe.workingHeadPosition(outputPipe);
		
		final int pSize = Pipe.addMsgIdx(outputPipe, NetPayloadSchema.MSG_PLAIN_210); 	
		  
		Pipe.addLongValue(clientConnection.id, outputPipe); //, NetPayloadSchema.MSG_PLAIN_210_FIELD_CONNECTIONID_201, clientConnection.id);
		Pipe.addLongValue(now, outputPipe);
		Pipe.addLongValue(0, outputPipe); // NetPayloadSchema.MSG_PLAIN_210_FIELD_POSITION_206, 0);
		
		int userId = Pipe.takeInt(requestPipe);
		int port   = Pipe.takeInt(requestPipe);
		int hostMeta = Pipe.takeRingByteMetaData(requestPipe);
		int hostLen  = Pipe.takeRingByteLen(requestPipe);
		int hostPos = Pipe.bytePosition(hostMeta, requestPipe, hostLen);
		long connId = Pipe.takeLong(requestPipe);
		
		int meta = Pipe.takeRingByteMetaData(requestPipe); //ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PATH_3
		int len  = Pipe.takeRingByteLen(requestPipe);
		boolean prePendSlash = (0==len) || ('/' != Pipe.byteBackingArray(meta, requestPipe)[Pipe.bytePosition(meta, requestPipe, len)&Pipe.blobMask(requestPipe)]);  
		
		int headersMeta = Pipe.takeRingByteMetaData(requestPipe); // HEADER
		int headersLen  = Pipe.takeRingByteMetaData(requestPipe);
		int headersPos  = Pipe.bytePosition(headersMeta, requestPipe, headersLen);
		
		DataOutputBlobWriter<NetPayloadSchema> activeWriter = Pipe.outputStream(outputPipe);
		DataOutputBlobWriter.openField(activeWriter);
	
		if (prePendSlash) { //NOTE: these can be pre-coverted to bytes so we need not convert on each write. may want to improve.
			DataOutputBlobWriter.write(activeWriter,HTTPClientRequestStage.GET_BYTES_SPACE_SLASH, 0, HTTPClientRequestStage.GET_BYTES_SPACE_SLASH.length);
	
		} else {
			DataOutputBlobWriter.write(activeWriter,HTTPClientRequestStage.GET_BYTES_SPACE, 0, HTTPClientRequestStage.GET_BYTES_SPACE.length);
		}
		
		//Reading from UTF8 field and writing to UTF8 encoded field so we are doing a direct copy here.
		Pipe.readBytes(requestPipe, activeWriter, meta, len);//, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PATH_3, activeWriter);
		
		HeaderUtil.writeHeaderBeginning(Pipe.byteBackingArray(hostMeta, requestPipe), hostPos, hostLen, Pipe.blobMask(requestPipe), activeWriter);
		
		HeaderUtil.writeHeaderMiddle(activeWriter, HTTPClientRequestStage.implementationVersion);
		activeWriter.write(Pipe.byteBackingArray(headersMeta, requestPipe), headersPos, headersLen, Pipe.blobMask(requestPipe));
		Pipe.readBytes(requestPipe, activeWriter, headersMeta, headersLen);
		
		HeaderUtil.writeHeaderEnding(activeWriter, true, (long) 0);  
		
		int msgLen = DataOutputBlobWriter.closeLowLevelField(activeWriter);//, NetPayloadSchema.MSG_PLAIN_210_FIELD_PAYLOAD_204);
	
		
		Pipe.confirmLowLevelWrite(outputPipe,pSize);
		Pipe.publishWrites(outputPipe);
		
		
	
	}

}
