using Toybox.Graphics;
using Toybox.Timer;
using Toybox.WatchUi;

class SessionView extends WatchUi.View {
    hidden var ticker;
    // A string resource id is not a String: it has to be loaded before it can be drawn
    // or joined to another string, or the watch draws a number. Loaded once here.
    hidden var textStartOnPhone;
    hidden var textWaiting;
    hidden var textConnected;
    hidden var textSet;
    hidden var textReps;
    hidden var textRest;
    hidden var textLogSet;
    hidden var textOffline;
    hidden var accent;

    function initialize() {
        View.initialize();
        ticker = new Timer.Timer();
        textStartOnPhone = WatchUi.loadResource(Rez.Strings.StartOnPhone);
        textWaiting = WatchUi.loadResource(Rez.Strings.Waiting);
        textConnected = WatchUi.loadResource(Rez.Strings.Connected);
        textSet = WatchUi.loadResource(Rez.Strings.Set);
        textReps = WatchUi.loadResource(Rez.Strings.Reps);
        textRest = WatchUi.loadResource(Rez.Strings.Rest);
        textLogSet = WatchUi.loadResource(Rez.Strings.LogSet);
        textOffline = WatchUi.loadResource(Rez.Strings.Offline);
        // The one accent, `#FFD60A`: Connect IQ has no token theme, so the value is
        // named here instead of using its unrelated COLOR_YELLOW.
        accent = Graphics.createColor(255, 255, 214, 10);
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
                textStartOnPhone,
                Graphics.TEXT_JUSTIFY_CENTER
            );
            dc.drawText(
                w / 2,
                h / 2 + 14,
                Graphics.FONT_TINY,
                OpenHealth.Model.connected ? textConnected : textWaiting,
                Graphics.TEXT_JUSTIFY_CENTER
            );
            return;
        }

        dc.setColor(accent, Graphics.COLOR_TRANSPARENT);
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
            OpenHealth.Model.reps.toString() + " " + textReps,
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
            textSet + " " + setNumber.toString() + " / " +
                OpenHealth.Model.targetSets.toString(),
            Graphics.TEXT_JUSTIFY_CENTER
        );

        var rest = OpenHealth.Model.restSeconds();
        if (rest > 0) {
            dc.setColor(accent, Graphics.COLOR_TRANSPARENT);
            dc.drawText(
                w / 2,
                h / 2 + 48,
                Graphics.FONT_SMALL,
                textRest + " " + clock(rest),
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
            OpenHealth.Model.connected ? textLogSet : textOffline,
            Graphics.TEXT_JUSTIFY_CENTER
        );
    }

    function weightText() {
        var kg = OpenHealth.Model.weightKg;
        if (OpenHealth.Model.weightUnit.equals("lb")) {
            kg = kg / 0.45359237;
        }
        var tenths = (kg * 10).toNumber();
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
