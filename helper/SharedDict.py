from multiprocessing.managers import AcquirerProxy, BaseManager, DictProxy


#https://stackoverflow.com/questions/57734298/how-can-i-provide-shared-state-to-my-flask-app-with-multiple-workers-without-dep
def get_shared_state():
    shared_dict = {}
    manager = BaseManager(("127.0.0.1", 35791), b"pykakao")
    manager.register("get_dict", lambda: shared_dict, DictProxy)
    try:
        manager.get_server()
        manager.start()
    except OSError:  # Address already in use
        manager.connect()
    return manager.get_dict()