using Toybox.WatchUi;

class SessionDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() {
        if (OpenHealth.Model.logSet()) {
            if (BleLink.instance != null) {
                BleLink.instance.sendPending();
            }
            WatchUi.requestUpdate();
        }
        return true;
    }

    function onPreviousPage() {
        OpenHealth.Model.adjustWeight(-1);
        WatchUi.requestUpdate();
        return true;
    }

    function onNextPage() {
        OpenHealth.Model.adjustReps(1);
        WatchUi.requestUpdate();
        return true;
    }

    function onMenu() {
        OpenHealth.Model.finish();
        if (BleLink.instance != null) {
            BleLink.instance.sendPending();
        }
        WatchUi.requestUpdate();
        return true;
    }
}
