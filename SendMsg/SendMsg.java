import android.os.Binder;
import android.os.IBinder;
import android.os.ServiceManager;
import android.app.IActivityManager;
import android.content.Intent;
import android.content.ComponentName;
import android.app.RemoteInput;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import android.util.Base64;
import java.io.File;
import java.io.FileReader;

class SendMsg {
    private static IBinder binder = ServiceManager.getService("activity");
    private static IActivityManager activityManager = IActivityManager.Stub.asInterface(binder);
    private static final int PORT = 3000;
    private static final String NOTI_REF; // Static final variable for NotificationReferer

    static {
        String notiRefValue = null;
        File prefsFile = new File("/data/data/com.kakao.talk/shared_prefs/KakaoTalk.hw.perferences.xml");
        BufferedReader prefsReader = null;
        try {
            prefsReader = new BufferedReader(new FileReader(prefsFile));
            String line;
            while ((line = prefsReader.readLine()) != null) {
                if (line.contains("<string name=\"NotificationReferer\">")) {
                    int start = line.indexOf(">") + 1;
                    int end = line.indexOf("</string>");
                    notiRefValue = line.substring(start, end);
                    break; // Found it, no need to read further
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading preferences file: " + e.toString());
            notiRefValue = "default_noti_ref"; // Fallback in case of error, or handle error more explicitly
        } finally {
            if (prefsReader != null) {
                try {
                    prefsReader.close();
                } catch (IOException e) {
                    System.err.println("Error closing preferences file reader: " + e.toString());
                }
            }
        }

        if (notiRefValue == null || notiRefValue.equals("default_noti_ref")) {
            System.err.println("NotificationReferer not found in preferences file or error occurred, using default or potentially failed to load.");
        } else {
            System.out.println("NotificationReferer loaded: " + notiRefValue);
        }
        NOTI_REF = (notiRefValue != null) ? notiRefValue : "default_noti_ref"; // Ensure NOTI_REF is always initialized
    }


    public static void main(String[] args) {
        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server listening on port " + PORT);

            while (true) {
                Socket clientSocket = null;
                BufferedReader in = null;
                PrintWriter out = null;
                try {
                    clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    out = new PrintWriter(clientSocket.getOutputStream(), true);

                    String line;
                    while ((line = in.readLine()) != null) {
                        try {
                            long start = System.currentTimeMillis();
                            System.out.println("Message received: " + line);
                            JSONObject obj = new JSONObject(line);
                            String encodedMsg = obj.getString("data");
                            String decodedMsg = new String(Base64.decode(encodedMsg, Base64.DEFAULT));

                            String encodedRoom = obj.getString("room");
                            String decodedRoom = new String(Base64.decode(encodedRoom, Base64.DEFAULT));
                            Long chatId = Long.parseLong(decodedRoom);


                            SendMessage(NOTI_REF, chatId, decodedMsg);
                            long end = System.currentTimeMillis();

                            Map mmap = new HashMap<String, Object>();
                            mmap.put("success", true);
                            mmap.put("time", end - start);
                            String successJson = new JSONObject(mmap).toString();

                            out.println(successJson);
                        } catch (Exception e) {
                            Map map = new HashMap<String, Object>();
                            map.put("success", false);
                            map.put("error", e.toString());

                            out.println(new JSONObject(map).toString());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("IO Exception in client connection: " + e.toString());
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                        if (in != null) {
                            in.close();
                        }
                        if (clientSocket != null) {
                            clientSocket.close();
                        }
                    } catch (IOException e) {
                        System.err.println("Error closing socket resources: " + e.toString());
                    }
                    System.out.println("Client disconnected");
                }
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT + ": " + e.toString());
            System.exit(1);
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing server socket: " + e.toString());
                }
            }
        }
    }

    private static void SendMessage(String notiRef, Long chatId, String msg) throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.kakao.talk", "com.kakao.talk.notification.NotificationActionService"));

        intent.putExtra("noti_referer", notiRef);
        intent.putExtra("chat_id", chatId);
        intent.setAction("com.kakao.talk.notification.REPLY_MESSAGE");

        Bundle results = new Bundle();
        results.putCharSequence("reply_message", msg);

        RemoteInput remoteInput = new RemoteInput.Builder("reply_message").build();
        RemoteInput[] remoteInputs = new RemoteInput[]{remoteInput};
        RemoteInput.addResultsToIntent(remoteInputs, intent, results);
        activityManager.startService(
            null,
            intent,
            intent.getType(),
            false,
            "com.android.shell",
            null,
            -2
        );
    }
}