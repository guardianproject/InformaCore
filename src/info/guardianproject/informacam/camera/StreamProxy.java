package info.guardianproject.informacam.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.commons.io.IOUtils;

import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;

public class StreamProxy implements Runnable {

    private Thread thread;
    private boolean isRunning;
    private ServerSocket socket;
    private int port;

    private final static String TAG = "StreamProxy";
    
    public StreamProxy(int serverPort) {

        // Create listening socket
        try {
          socket = new ServerSocket(serverPort, 0, InetAddress.getByAddress(new byte[] {127,0,0,1}));
          socket.setSoTimeout(5000);
          port = socket.getLocalPort();
        } catch (UnknownHostException e) { // impossible
        } catch (IOException e) {
          Log.e(TAG, "IOException initializing server", e);
        }

    }

    public void start() {
        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        isRunning = false;
        thread.interrupt();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
    }

    @Override
      public void run() {
        Looper.prepare();
        isRunning = true;
        while (isRunning) {
          try {
            Socket client = socket.accept();
            if (client == null) {
              continue;
            }
            Log.d(TAG, "client connected");

            StreamToMediaPlayerTask task = new StreamToMediaPlayerTask(client);
            if (task.processRequest()) {
                task.execute();
            }

          } catch (SocketTimeoutException e) {
            // Do nothing
          } catch (IOException e) {
            Log.e(TAG, "Error connecting to client", e);
          }
        }
        Log.d(TAG, "Proxy interrupted. Shutting down.");
      }




    private class StreamToMediaPlayerTask extends AsyncTask<String, Void, Integer> {

        String localPath;
        Socket client;
        int cbSkip;

        public StreamToMediaPlayerTask(Socket client) {
            this.client = client;
        }

        public boolean processRequest() {
            // Read HTTP headers
            String headerText = null;
            String[] headers = null;
            try {
              //headers = Utils.readTextStreamAvailable(client.getInputStream());
            	headerText = IOUtils.toString(client.getInputStream());
            	headers = headerText.split("\n");
            } catch (Exception e) {
              Log.e(TAG, "Error reading HTTP request header from stream:", e);
              return false;
            }

            // Get the important bits from the headers

            String urlLine = headers[0];
            if (!urlLine.startsWith("GET ")) {
                Log.e(TAG, "Only GET is supported");
                return false;               
            }
            urlLine = urlLine.substring(4);
            int charPos = urlLine.indexOf(' ');
            if (charPos != -1) {
                urlLine = urlLine.substring(1, charPos);
            }
            localPath = urlLine;

            // See if there's a "Range:" header
            for (String headerLine : headers) {

                if (headerLine.startsWith("Range: bytes=")) {
                    headerLine = headerLine.substring(13);
                    charPos = headerLine.indexOf('-');
                    if (charPos>0) {
                        headerLine = headerLine.substring(0,charPos);
                    }
                    cbSkip = Integer.parseInt(headerLine);
                }
            }
            return true;
        }

        @Override
        protected Integer doInBackground(String... params) {

           long fileSize = 10;//GET CONTENT LENGTH HERE;
           String mimeType = null;
           
            // Create HTTP header
            String headers = "HTTP/1.0 200 OK\r\n";
            headers += "Content-Type: " + mimeType + "\r\n";
            headers += "Content-Length: " + fileSize  + "\r\n";
            headers += "Connection: close\r\n";
            headers += "\r\n";

            // Begin with HTTP header
            int fc = 0;
            long cbToSend = fileSize - cbSkip;
            OutputStream output = null;
            byte[] buff = new byte[64 * 1024];
            try {
                output = new BufferedOutputStream(client.getOutputStream(), 32*1024);                           
                output.write(headers.getBytes());

                // Loop as long as there's stuff to send
                while (isRunning && cbToSend>0 && !client.isClosed()) {

                    // See if there's more to send
                    File file = new File(localPath);
                    fc++;
                    int cbSentThisBatch = 0;
                    if (file.exists()) {
                        FileInputStream input = new FileInputStream(file);
                        input.skip(cbSkip);
                        int cbToSendThisBatch = input.available();
                        while (cbToSendThisBatch > 0) {
                            int cbToRead = Math.min(cbToSendThisBatch, buff.length);
                            int cbRead = input.read(buff, 0, cbToRead);
                            if (cbRead == -1) {
                                break;
                            }
                            cbToSendThisBatch -= cbRead;
                            cbToSend -= cbRead;
                            output.write(buff, 0, cbRead);
                            output.flush();
                            cbSkip += cbRead;
                            cbSentThisBatch += cbRead;
                        }
                        input.close();
                    }

                    // If we did nothing this batch, block for a second
                    if (cbSentThisBatch == 0) {
                        Log.d(TAG, "Blocking until more data appears");
                        Thread.sleep(1000);
                    }
                }
            }
            catch (SocketException socketException) {
                Log.e(TAG, "SocketException() thrown, proxy client has probably closed. This can exit harmlessly");
            }
            catch (Exception e) {
                Log.e(TAG, "Exception thrown from streaming task:");
                Log.e(TAG, e.getClass().getName() + " : " + e.getLocalizedMessage());
                e.printStackTrace();                
            }

            // Cleanup
            try {
                if (output != null) {
                    output.close();
                }
                client.close();
            }
            catch (IOException e) {
                Log.e(TAG, "IOException while cleaning up streaming task:");                
                Log.e(TAG, e.getClass().getName() + " : " + e.getLocalizedMessage());
                e.printStackTrace();                
            }

            return 1;
        }

    }
}