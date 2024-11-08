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
    var ins = socket.getInputStream();
    var br = new java.io.BufferedReader(new java.io.InputStreamReader(ins));
    var line = "";
    while ((line = br.readLine()) != null) {
        if (line == "")
            break;
        if (line.startsWith("{")){
            let msg = line;
            replyResult(msg);
        }
    }
    ins.close();
    socket.close();
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
            Api.replyToID(data.room,data.data);
    }
}
