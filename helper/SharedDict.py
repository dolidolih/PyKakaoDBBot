from multiprocessing.managers import AcquirerProxy, BaseManager, DictProxy

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