package com.example.peeler;

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

class MDNSSD {
	static private byte[] queryPacketHeader = {
		0x00, 0x00, // transcation id
		0x00, 0x00, // flags
		0x00, 0x00, // num of questions
		0x00, 0x00, // num of answer rrs
		0x00, 0x00, // num of auth rrs
		0x00, 0x00 // num of additional rrs
	};
	static private int numQuestionsIndex = 5; 

	static private byte[] questionFooter = {
		0x00, 0x0c, // type = domain name ptr
		0x00, 0x01 // 0b0 "qu" question = false; 0b... class = in
	};

	static List<MDNSRecord> findServices(String[] types) {
		try {
			MulticastSocket socket = new MulticastSocket(5353);
			socket.setSoTimeout(1000);
			socket.setTimeToLive(255);

			return doFind(types, socket);
		} catch (IOException e) {
			return new ArrayList<MDNSRecord>();
		}
	}

	static private List<MDNSRecord> doFind(String[] types, MulticastSocket socket) throws IOException {
		List<MDNSRecord> services = new ArrayList<MDNSRecord>();
		DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);

		socket.send(createQuery(types));

		while (true) {
			try { socket.receive(packet); } catch (InterruptedIOException e) { return services; }

			List<MDNSRecord> records = parsePacket(packet);
			if (records == null) continue;

			MDNSRecord service = null;
			for (MDNSRecord r : records) {
				if (r.type == MDNSRecord.Type.SRV) {
					for (String type : types) {
						if (r.name.endsWith(type)) {
							service = r;
							break;
						}
					}
					if (service != null) break;
				}
			}
			if (service != null) {
				for (MDNSRecord s : services) {
					if (service.name.equals(s.name)) {
						service = null;
						break;
					}
				}
				if (service == null) continue;

				for (MDNSRecord r : records) {
					if ((r.type == MDNSRecord.Type.A || r.type == MDNSRecord.Type.AAAA) && service.target.equals(r.name))
						service.addresses.addAll(r.addresses);
				}
				services.add(service);
			}
		}
	}

	static private DatagramPacket createQuery(String[] types) {
		int totalLength = queryPacketHeader.length + ((256 + questionFooter.length) * types.length);
		byte[] data = new byte[totalLength];
		int index = queryPacketHeader.length;
		System.arraycopy(queryPacketHeader, 0, data, 0, index);

		data[numQuestionsIndex] = (byte)types.length;

		for (String type : types) {
			byte[] typeBytes = typeToBytes(type);
			System.arraycopy(typeBytes, 0, data, index, typeBytes.length);
			index += typeBytes.length;
			System.arraycopy(questionFooter, 0, data, index, questionFooter.length);
			index += questionFooter.length;
		}

		try {
			return new DatagramPacket(data, index, InetAddress.getByName("224.0.0.251"), 5353);
		} catch (UnknownHostException e) { return null; /* won't happen */ }
	}
	
	static private byte[] typeToBytes(String type) {
		int index = 0;
		byte[] typeBytes = new byte[type.length() + 2];

		for (String part : type.split("\\.")) {
			if (part.length() > 255) part = part.substring(0, 254);
			byte[] partBytes = null;
			try { partBytes = part.getBytes("us-ascii"); } catch (UnsupportedEncodingException e) { /* won't happen */ }

			typeBytes[index++] = (byte)partBytes.length;
			System.arraycopy(partBytes, 0, typeBytes, index, partBytes.length);
			index += partBytes.length;
		}
		typeBytes[index] = 0x00;

		return typeBytes;
	}
	
	static List<MDNSRecord> parsePacket(DatagramPacket packet) {
		int index = 0;
		byte[] data = packet.getData();

		int tid = (((data[index++] & 0xff) << 8) | (data[index++] & 0xff));
		int flags = (((data[index++] & 0xff) << 8) | (data[index++] & 0xff));
		if ((flags & 0x8000) == 0) return null;

		int numQuestions = (((data[index++] & 0xff) << 8) | (data[index++] & 0xff));
		index += 6;

		return MDNSRecord.parseRecords(packet, data, index, numQuestions);
	}
}


class MDNSRecord {
	public enum Type {
		UNKNOWN (0), A (0x01), PTR (0x0c), TXT (0x10), AAAA (0x1c), SRV (0x21);

		public int value;
		Type(int value) { this.value = value; }
		static public Type fromValue(int value) {
			for (Type type : values()) {
				if (type.value == value) return type;
			}
			return UNKNOWN;
		}
	};

	public enum Clas {
		UNKNOWN (0), IN (0x01);

		public int value;
		Clas(int value) { this.value = value; }
		static public Clas fromValue(int value) {
			for (Clas clas : values()) {
				if (clas.value == value) return clas;
			}
			return UNKNOWN;
		}
	};

	String name, domainName, target;
	Type type;
	Clas clas;
	boolean randBool;
	int TTL, priority, weight, port;
	Map<String, String> textPairs;
	byte[] data;
	ArrayList<InetAddress> addresses;

	MDNSRecord(String name, Type type, Clas clas, boolean randBool, int TTL) {
		this.name = name;
		this.type = type;
		this.clas = clas;
		this.randBool = randBool;
		this.TTL = TTL;

		data = null;
		domainName = target = null;
		textPairs = null;
		addresses = new ArrayList<InetAddress>();
		priority = weight = port = -1;
	}

	MDNSRecord(String name, Type type, Clas clas, boolean randBool) {
		this(name, type, clas, randBool, -1);
	}
	
	public String toString() {
		String str = "name: " + name + "\n";
		str += "type: " + type + "\n";
		str += "class: " + clas + "\n";
		str += "rand bool: " + randBool + "\n";
		str += "TTL: " + TTL + "\n";
		for (InetAddress addy : addresses)
				str += "address: " + addy + "\n";
		if (textPairs != null) {
			for (Entry<String, String> kv : textPairs.entrySet())
				str += kv.getKey() + ": " + kv.getValue() + "\n";
		} else if (domainName != null)
			str += "domain name: " + domainName + "\n";
		else if (port > -1) {
			str += "priority: " + priority + "\n";
			str += "weight: " + weight + "\n";
			str += "port: " + port + "\n";
			str += "target: " + target + "\n";
		} else
			str += "data: " + (data != null) + "\n";
		return str;
	}

	static List<MDNSRecord> parseRecords(DatagramPacket packet, byte[] data, int index, int numQuestions) {
		ArrayList<MDNSRecord> list = new ArrayList<MDNSRecord>();

		while (index < data.length && data[index] != 0) {
			AtomicInteger mutIndex = new AtomicInteger(index);
			String name = parseName(data, mutIndex);
			index = mutIndex.get();

			Type type = Type.fromValue((data[index++] & 0xff) << 8 | (data[index++] & 0xff));
			boolean randBool = (data[index] & 0x80) == 0x80;
			Clas clas = Clas.fromValue(((data[index++] & 0x7f) << 8) | (data[index++] & 0xff));

			if (numQuestions > 0) {
				numQuestions--;
				list.add(new MDNSRecord(name, type, clas, randBool));
				continue;
			}

			int TTL = (data[index++] & 0xff) << 24;
			TTL |= (data[index++] & 0xff) << 16;
			TTL |= (data[index++] & 0xff) << 8;
			TTL |= (data[index++] & 0xff);

			int length = (((data[index++] & 0xff) << 8) | (data[index++] & 0xff));
			mutIndex.set(index);
			index += length;
			byte[] info = new byte[index];
			System.arraycopy(data, 0, info, 0, index);

			MDNSRecord record = new MDNSRecord(name, type, clas, randBool, TTL);
			switch (type) {
				case PTR:
					record.domainName = parseName(info, mutIndex);
					break;
				case TXT:
					record.textPairs = parseKeyValues(info, mutIndex, !name.contains("_device-info"));
					break;
				case SRV:
					record.priority = (data[mutIndex.getAndIncrement()] & 0xff) << 8;
					record.priority |= (data[mutIndex.getAndIncrement()] & 0xff);
					record.weight = (data[mutIndex.getAndIncrement()] & 0xff) << 8;
					record.weight |= (data[mutIndex.getAndIncrement()] & 0xff);
					record.port = (data[mutIndex.getAndIncrement()] & 0xff) << 8;
					record.port |= (data[mutIndex.getAndIncrement()] & 0xff);
					record.target = parseName(data, mutIndex);
					break;
				case A:
				case AAAA:
					info = new byte[length];
					System.arraycopy(data, mutIndex.get(), info, 0, length);
					try {
						record.addresses.add(InetAddress.getByAddress(info));		
					} catch (UnknownHostException e) {
						record.addresses.add(packet.getAddress());
					}
				default:
					info = new byte[length];
					System.arraycopy(data, mutIndex.get(), info, 0, length);
					record.data = info;
			}

			list.add(record);
		}

		return list;
	}
	
	static String parseName(byte[] data, AtomicInteger mutIndex) {
		String name = "";
		for (String part : parseParts(data, mutIndex))
			name += part + ".";
		return name.substring(0, name.length() - 1);
	}

	static Map<String, String> parseKeyValues(byte[] data, AtomicInteger mutIndex, boolean subDivide) {
		TreeMap<String, String> map = new TreeMap<String, String>();
		for (String part : parseParts(data, mutIndex)) {
			if (subDivide) {
				for (String subPart : part.split(",")) {
					String[] kv = subPart.split("=", 2);
					map.put(kv[0], kv[1]);
				}
			} else {
				String[] kv = part.split("=", 2);
				map.put(kv[0], kv[1]);
			}
		}
		return map;
	}
	
	static List<String> parseParts(byte[] data, AtomicInteger mutIndex) {
		ArrayList<String> parts = new ArrayList<String>();
		int index = mutIndex.get();
		while (index < data.length) {
			int partLen = (data[index++] & 0xff);
			if (partLen == 0)
				break;
			else if ((partLen & 0xc0) == 0xc0) {
				parts.addAll(parseParts(data, new AtomicInteger(((partLen & 0x3f) << 8) | (data[index++] & 0xff))));
				break;
			}

			try {
				parts.add(new String(data, index, partLen, "us-ascii"));
				index += partLen;
			} catch (UnsupportedEncodingException e) { /* won't happen */ }
		}
		mutIndex.set(index);
		return parts;
	}
}