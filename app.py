# coding: utf8
from flask import Flask,request,json
import base64
from chatbot.Response import response
from helper.Replier import Replier
from helper.KakaoDB import KakaoDB
from helper.SharedDict import get_shared_state
import time

app = Flask(__name__)
db = KakaoDB()
g = get_shared_state()

@app.route('/db',methods=['POST'])
def py_exec_db():
    r = app.response_class(
        response="200",
        status=200,
        mimetype='text/plain; charset="utf-8"'
        )
    request_data = json.loads(request.form['data'])
    replier = Replier(request_data)
    @r.call_on_close
    def on_close():
        response(request_data["room"],
            request_data["msg"],
            request_data["sender"],
            replier,
            request_data["json"],
            db,
            g
           )
    return r

if __name__ == "__main__":
    SharedState.register('state', SharedState._get_shared_state, NamespaceProxy)
    app.run(host='0.0.0.0', port=5000)