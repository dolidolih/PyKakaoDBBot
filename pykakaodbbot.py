import multiprocessing
import subprocess
import sys
import os
import signal

def main():
    proc1 = subprocess.Popen("venv/bin/gunicorn -b 0.0.0.0:5000 -w 9 app:app -t 100", shell = True)
    proc2 = subprocess.Popen("venv/bin/python dbobserver.py", shell=True)
    while True:
        if proc1.poll() is not None:
            print("killing dbobserver because flask died")
            os.killpg(os.getpgid(proc2.pid), signal.SIGTERM)
            break
        if proc2.poll() is not None:
            print("killing flask because dbobserver died")
            os.killpg(os.getpgid(proc1.pid), signal.SIGTERM)
            break
        
if __name__=="__main__":
    main()
