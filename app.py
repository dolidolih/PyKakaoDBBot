# coding: utf8
from flask import Flask,request,json
import base64
from chatbot.Response import response
from helper.Replier import Replier

app = Flask(__name__)

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
            replier,request_data["json"]
           )
    return r

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=5000)
