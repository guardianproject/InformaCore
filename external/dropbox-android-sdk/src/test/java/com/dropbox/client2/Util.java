package com.dropbox.client2;

import java.io.*;
import java.util.Map;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class Util {
    @SuppressWarnings("rawtypes")
    public static Map loadConfig(String path) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(path), 8192);
            String inputLine = null;
            String result = "";

            try {
                while ((inputLine = in.readLine()) != null)
                    result += inputLine;
            } finally {
                in.close();
            }

            JSONParser parser = new JSONParser();
            try {
                return (Map) parser.parse(result);
            } catch (ParseException e) {
                e.printStackTrace();
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }
    public static byte[] streamToBytes(InputStream in) throws IOException{

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();

        return buffer.toByteArray();
    }


}
