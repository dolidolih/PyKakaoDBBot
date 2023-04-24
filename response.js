// tcp socket by Matsogeum
// https://cafe.naver.com/nameyee/4195
var thread1 = new java.lang.Thread({
    run: function() {
        try {
            listener = new java.net.ServerSocket(3000);
            try {
                while (1) {
                    _socket = listener.accept();
                    new java.lang.Thread({
                        run: function() {
                            try {
                                SocketHandler(_socket);
                            }  catch (e) {
                                _socket.close();
                                Log.e(e);
                            }
                        }
                    }).start();
                }
            } catch (e) {
                Log.e(e);
                listener.close();
            }
        }  catch (e) {
            Log.e(e);
        }
    }
});
thread1.start();

function onStartCompile() {
    listener.close();
}

function SocketHandler(socket) {
    ins = socket.getInputStream();
    br = new java.io.BufferedReader(new java.io.InputStreamReader(ins));
    line = "";
    while ((line = br.readLine()) != null) {
        if (line == "")
            break;
        if (line.startsWith("{")){
            msg = line;
        }
    }
    ins.close();
    socket.close();
    replyResult(msg);
}

function base64Decode(input) {
    let decoder = new Packages.sun.misc.BASE64Decoder();
    let decodedByteArray = decoder.decodeBuffer(input);
    let decodedString = new java.lang.String(decodedByteArray, "UTF-8");
    return decodedString+"";
}

function replyResult(data){
    data = JSON.parse(data);

    data.data = base64Decode(data.data);
    data.room = base64Decode(data.room);
    data.msgJson = JSON.parse(base64Decode(data.msgJson));
    if (data.isSuccess) {
            Api.replyRoom(data.room,data.data);
    }
}

// response fix by dark tornado
function onNotificationPosted(sbn, sm) {
    var packageName = sbn.getPackageName();
    if (!packageName.startsWith("com.kakao.tal")) return;
    var actions = sbn.getNotification().actions;
    if (actions == null) return;
    var userId = sbn.getUser().hashCode();
    for (var n = 0; n < actions.length; n++) {
        var action = actions[n];
        if (action.getRemoteInputs() == null) continue;
        var bundle = sbn.getNotification().extras;
        var msg = bundle.get("android.text").toString();
        var sender = bundle.getString("android.title");
        var room = bundle.getString("android.subText");
        if (room == null) room = bundle.getString("android.summaryText");
        var isGroupChat = room != null;
        if (room == null) room = sender;
        var replier = new com.xfl.msgbot.script.api.legacy.SessionCacheReplier(packageName, action, room, false, "");
        com.xfl.msgbot.application.service.NotificationListener.Companion.setSession(packageName, room, action);
    }
}