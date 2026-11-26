using Toybox.Graphics;
using Toybox.Timer;
using Toybox.WatchUi;

class SessionView extends WatchUi.View {
    hidden var ticker;

    function initialize() {
        View.initialize();
        ticker = new Timer.Timer();
    }

    function onShow() {
        ticker.start(method(:tick), 1000, true);
    }

    function onHide() {
        ticker.stop();
    }

    function tick() {
        if (BleLink.instance != null) {
            BleLink.instance.sendPending();
        }
        WatchUi.requestUpdate();
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, 6, Graphics.FONT_TINY, "openHealth", Graphics.TEXT_JUSTIFY_CENTER);

        if (!OpenHealth.Model.hasSession()) {
            dc.drawText(
                w / 2,
                h / 2 - 12,
                Graphics.FONT_SMALL,
                Rez.Strings.StartOnPhone,
                Graphics.TEXT_JUSTIFY_CENTER
            );
            dc.drawText(
                w / 2,
                h / 2 + 14,
                Graphics.FONT_TINY,
                OpenHealth.Model.connected ? Rez.Strings.Connected : Rez.Strings.Waiting,
                Graphics.TEXT_JUSTIFY_CENTER
            );
            return;
        }

        dc.setColor(Graphics.COLOR_YELLOW, Graphics.COLOR_TRANSPARENT);
        dc.drawText(
            w / 2,
            h / 2 - 24,
            Graphics.FONT_LARGE,
            weightText(),
            Graphics.TEXT_JUSTIFY_CENTER
        );
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(
            w / 2,
            h / 2 + 6,
            Graphics.FONT_MEDIUM,
            OpenHealth.Model.reps.toString() + " " + Rez.Strings.Reps,
            Graphics.TEXT_JUSTIFY_CENTER
        );
        var setNumber = OpenHealth.Model.doneSets + 1;
        if (OpenHealth.Model.targetSets > 0 && setNumber > OpenHealth.Model.targetSets) {
            setNumber = OpenHealth.Model.targetSets;
        }
        dc.drawText(
            w / 2,
            h / 2 + 30,
            Graphics.FONT_TINY,
            Rez.Strings.Set + " " + setNumber.toString() + " / " +
                OpenHealth.Model.targetSets.toString(),
            Graphics.TEXT_JUSTIFY_CENTER
        );

        var rest = OpenHealth.Model.restSeconds();
        if (rest > 0) {
            dc.setColor(Graphics.COLOR_YELLOW, Graphics.COLOR_TRANSPARENT);
            dc.drawText(
                w / 2,
                h / 2 + 48,
                Graphics.FONT_SMALL,
                Rez.Strings.Rest + " " + clock(rest),
                Graphics.TEXT_JUSTIFY_CENTER
            );
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        }
        if (OpenHealth.Model.heartRate > 0) {
            dc.drawText(
                w / 2,
                h - 30,
                Graphics.FONT_TINY,
                OpenHealth.Model.heartRate.toString() + " bpm",
                Graphics.TEXT_JUSTIFY_CENTER
            );
        }
        dc.drawText(
            w / 2,
            h - 14,
            Graphics.FONT_TINY,
            OpenHealth.Model.connected ? Rez.Strings.LogSet : Rez.Strings.Offline,
            Graphics.TEXT_JUSTIFY_CENTER
        );
    }

    function weightText() {
        var tenths = (OpenHealth.Model.weightKg * 10).toNumber();
        var whole = (tenths / 10).toNumber();
        var fraction = tenths % 10;
        return whole.toString() + "." + fraction.toString() + " " + OpenHealth.Model.weightUnit;
    }

    function clock(seconds) {
        var minutes = (seconds / 60).toNumber();
        var rest = seconds % 60;
        var restText = rest < 10 ? "0" + rest.toString() : rest.toString();
        return minutes.toString() + ":" + restText;
    }
}
