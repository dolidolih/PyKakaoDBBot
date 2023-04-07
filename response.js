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
}}).start();
      }
    }    catch (e) {
  Log.e(e);
  listener.close();
}
  }  catch (e) {
  Log.e(e);
}
}});
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
        if (data.type == "eval") {
          data.data = JSON.parse(data.data);
            if (data.data.isAdmin == "admin") {
                try{
                    Api.replyRoom(data.room,eval(data.data.msg.substring(3)));
                } catch (e){
                    Api.replyRoom(data.room,e);
                }
            }
        } else {
                Api.replyRoom(data.room,data.data);
        }
    }
}
