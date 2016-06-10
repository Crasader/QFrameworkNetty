package HttpFileServer.App;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import javax.activation.MimetypesFileTypeMap;



@SuppressWarnings("restriction")
public class HttpFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private final String url;
	
	
	public HttpFileServerHandler(String url) {
		// TODO Auto-generated constructor stub
		this.url = url;
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {

		if (!request.decoderResult().isSuccess()) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST);
			return;
		}

		if (request.method() != HttpMethod.GET) {
			sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
			return;
		}

		final String uri = request.uri();
		final String path = sanitizeUri(uri);
		if (path == null) {
			sendError(ctx, HttpResponseStatus.FORBIDDEN);
			return;
		}

		File file = new File(path);
		if (file.isHidden() || !file.exists()) {
			sendError(ctx, HttpResponseStatus.NOT_FOUND);
			return;
		}

		if (file.isDirectory()) {
			if (uri.endsWith("/")) {
				sendListing(ctx, file);
			} else {
				 sendRedirect(ctx, uri + "/");
			}
			return;
		}
		
		if (!file.isFile()) {
			sendError(ctx, HttpResponseStatus.FORBIDDEN);
			return;
		}
		
		RandomAccessFile randomAccessFile = null;
		try {
			randomAccessFile = new RandomAccessFile(file, "r");// 以只读的方式打开文件
		} catch (FileNotFoundException fnfe) {
			sendError(ctx, HttpResponseStatus.NOT_FOUND);
			return;
		}
		
		long fileLength = randomAccessFile.length();
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
		HttpUtil.setContentLength(response, fileLength);
		setContentTypeHeader(response, file);
		if (HttpUtil.isKeepAlive(request)) {
		    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
		}
		ctx.write(response);
		ChannelFuture sendFileFuture;
		sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile, 0,
			fileLength, 8192), ctx.newProgressivePromise());
		sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
		    public void operationProgressed(ChannelProgressiveFuture future,
			    long progress, long total) {
			if (total < 0) { // total unknown
			    System.err.println("Transfer progress: " + progress);
			} else {
			    System.err.println("Transfer progress: " + progress + " / "
				    + total);
			}
		    }

		    public void operationComplete(ChannelProgressiveFuture future)
			    throws Exception {
			System.out.println("Transfer complete.");
		    }
		});
		ChannelFuture lastContentFuture = ctx
			.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		if (!HttpUtil.isKeepAlive(request)) {
		    lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
		
		
	}

	private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

	private String sanitizeUri(String uri) {
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			try {
				uri = URLDecoder.decode(uri, "IOS-8859-1");
			} catch (UnsupportedEncodingException e2) {
				throw new Error();
			}
		}

		if (!uri.startsWith(url)) {
			return null;
		}

		if (!uri.startsWith("/")) {
			return null;
		}

		uri = uri.replace("/", File.separator);
		if (uri.contains(File.separator + ".") || uri.contains("." + File.separator) || uri.startsWith(".")
				|| uri.endsWith(".") || INSECURE_URI.matcher(uri).matches()) {
			return null;
		}

		return System.getProperty("user.dir") + File.separator + uri;

	}

	private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

	private static void sendListing(ChannelHandlerContext ctx,File dir) {
	    	
	    	FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
	    	response.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/html; charset=UTF-8");
	    	StringBuilder buf = new StringBuilder();
	    	String dirPath = dir.getPath();
	    	buf.append("<!DOCTYPE html>\r\n");
	    	buf.append("<html><head><title>");
	    	buf.append(dirPath);
	    	buf.append(" 目录：");
	    	buf.append("</title></head><body>\r\n");
	    	buf.append("<h3>");
	    	buf.append(dirPath).append(" 目录：");
	    	buf.append("</h3>\r\n");
	    	buf.append("<ul>");
	    	buf.append("<li>链接：<a href=\"../\">..</a></li>\r\n");
	    	for (File f : dir.listFiles()) {
	    		if (f.isHidden() || !f.canRead()) {
	    			continue;
	    		}
	    		
	    		String name = f.getName();
	    		if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
	    			continue;
	    		}
	    		buf.append("<li>链接:<a href=\"");
	    		buf.append(name);
	    		buf.append("\">");
	    		buf.append(name);
	    	    buf.append("</a></li>\r\n");
	    	}
	    	buf.append("</ul></body></html>\r\n");
	    	ByteBuf buffer = Unpooled.copiedBuffer(buf,CharsetUtil.UTF_8);
	    	response.content().writeBytes(buffer);
	    	buffer.release();
	    	ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		}

	private  static void  sendRedirect(ChannelHandlerContext ctx,String newUri) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
		response.headers().set(HttpHeaderNames.LOCATION,newUri);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		
		
	}
	private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
				Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain;charset=UTF-8");
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

    private static void setContentTypeHeader(HttpResponse response, File file) {
	MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
	response.headers().set(HttpHeaderNames.CONTENT_TYPE,
		mimeTypesMap.getContentType(file.getPath()));
    }


}
