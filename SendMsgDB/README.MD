# SendMsgDB

### To run,
```shell
adb push SendMsgDB.dex /data/local/tmp
sdb push config.json /data/local/tmp
adb shell "su root sh -c 'CLASSPATH=/data/local/tmp/SendMsgDB.dex /system/bin/app_process / SendMsgDB' &"
```
### SendMsgDB will send below json msg via x-www-form-urlencoded post request with key named "data" :
```json
{
"msg" : decryptedMsg,
"room" : nameOfRoom,
"sender" : decryptedSender,
"json" : rowDBRecord
}
```

### SendMsgDB listens with TCP socket port 3000. See Replier.py for json message needed.

### You don't need to run dbobserver service if you use this.
