package com.example.peeler;

import android.util.Log;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;
import java.math.BigInteger;

class RTSP extends Socket {
	static final String requestFormat = "%s %s RTSP/1.0\r\n" +
		"CSeq: %s\r\n" +
		"User-Agent: iTunes/10.1.1 (Macintosh; Intel Mac OS X 10.6.6) AppleWebKit/533.19.4\r\n" +
		"Client-Instance: %s\r\n" +
		"DACP-ID: %s\r\n";

	private String url;

	public String clientId = new BigInteger(64, new Random()).toString(16);
	public int cseq = 1;
	public String response = null;

	RTSP(InetAddress dest, int port, String url) throws IOException {
		super(dest, port);
		this.url = url;
		setSoTimeout(5000);
	}

	RTSP(InetAddress dest, String url) throws IOException {
		this(dest, 5000, url);
	}

	public String[] send(String command, String url, String headers, String content, String contentType) throws RTSPException, IOException {
		String request = String.format(requestFormat, command, (url == null ? this.url : url), cseq++, clientId, clientId);
		if (headers != null) request += headers;
		if (content != null) {
			request += "Content-Type: " + contentType + "\r\n";
			request += "Content-Length: " + content.length() + "\r\n";
		}
		request += "\r\n";
		if (content != null) request += content;

		try {
			OutputStream output = getOutputStream();
			output.write(request.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) { /* won't happen */ }

		try {
			InputStream input = getInputStream();
			byte[] data = new byte[4096];
			int len = input.read(data);
			response = new String(data, 0, len, "utf-8");
		} catch (UnsupportedEncodingException e) { /* won't happen */ }

		String[] lines = response.split("\r\n");
		if (!lines[0].toLowerCase().startsWith("rtsp/1.0 200"))
			throw new RTSPException(lines[0]);

		return lines;
	}

	public String[] send(String command, String headers) throws RTSPException, IOException {
		return send(command, null, headers, null, null);
	}

	public String[] announce(String headers, String content) throws RTSPException, IOException {
		return send("ANNOUNCE", null, headers, content, "application/sdp");
	}

	public String[] setup(String headers) throws RTSPException, IOException {
		return send("SETUP", headers);
	}

	public String[] options(String headers) throws RTSPException, IOException {
		return send("OPTIONS", "*", headers, null, null);
	}

	public String[] record(String headers) throws RTSPException, IOException {
		return send("RECORD", headers);
	}

	public String[] setParameter(String headers, String content) throws RTSPException, IOException {
		return send("SET_PARAMETER", null, headers, content, "text/parameters");
	}

	public String[] flush(String headers) throws RTSPException, IOException {
		return send("FLUSH", headers);
	}

	public String[] teardown(String headers) throws RTSPException, IOException {
		return send("TEARDOWN", headers);
	}
}

class RTSPException extends Exception {
	RTSPException(String message) {
		super("Bad response: " + message);
	}
}