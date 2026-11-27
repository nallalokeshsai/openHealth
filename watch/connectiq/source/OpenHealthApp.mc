using Toybox.Application;

class OpenHealthApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        OpenHealth.Model.load();
        OpenHealth.Heart.start();
        if (BleLink.instance == null) {
            var link = new BleLink();
        }
        BleLink.instance.start();
    }

    function onStop(state) {
        if (BleLink.instance != null) {
            BleLink.instance.stop();
        }
    }

    function getInitialView() {
        return [new SessionView(), new SessionDelegate()];
    }
}
