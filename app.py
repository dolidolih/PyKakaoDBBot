# coding: utf8
from flask import Flask,request,json
from socketsender import send_via_socket
import base64

app = Flask(__name__)

@app.route('/db',methods=['POST'])
def py_exec_db():
    response = app.response_class(
        response="200",
        status=200,
        mimetype='text/plain; charset="utf-8"'
    )
    request_data = json.loads(request.form['data'])
    print("received")
    @response.call_on_close
    def on_close():
        send_via_socket(True,"string","hello",request_data["room"],"{}")
    return response

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=5000)
