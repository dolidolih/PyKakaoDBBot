from socket import *
import json
import base64

ip = "192.168.240.112"
port = 3000

def send_via_socket(is_success,type,data,room,msg_json):
    clientSocket = socket(AF_INET, SOCK_STREAM)
    clientSocket.connect((ip,port))

    res = { "isSuccess":is_success,
        "type":type,
        "data":base64.b64encode(data.encode()).decode(),
        "room":base64.b64encode(room.encode()).decode(),
        "msgJson":base64.b64encode(json.dumps(msg_json).encode()).decode()
        }
    
    clientSocket.send(json.dumps(res).encode("utf-8"))
    clientSocket.close()

def s(msg,room):
    clientSocket = socket(AF_INET, SOCK_STREAM)
    clientSocket.connect((ip,port))

    res = { "isSuccess":True,
        "type":"normal",
        "data":base64.b64encode(msg.encode()).decode(),
        "room":base64.b64encode(room.encode()).decode(),
        "userId":"123123123",
        "msgJson":base64.b64encode('[]'.encode()).decode()}

    clientSocket.send(json.dumps(res).encode("utf-8"))
    clientSocket.close()
